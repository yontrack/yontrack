package net.nemerosa.ontrack.extension.findings.search

import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.util.ObjectBuilder
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.FindingsExtensionFeature
import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingExposureState
import net.nemerosa.ontrack.extension.findings.query.FindingQueryService
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

/**
 * Searching the findings by their external ID — a CVE, a rule ID.
 *
 * One document per finding, indexed when the finding is first seen. Its external ID never
 * changes, so there is nothing to update afterwards. The findings of a deleted project stay in
 * the index until the next reindexation, but are no longer found: a result is read from the
 * database, and filtered by
 * [ProjectFindingsView][net.nemerosa.ontrack.extension.findings.security.ProjectFindingsView].
 */
@Component
class FindingSearchIndexer(
    extensionFeature: FindingsExtensionFeature,
    private val findingRepository: FindingRepository,
    private val findingQueryService: FindingQueryService,
    private val structureService: StructureService,
    private val searchIndexService: SearchIndexService,
    private val ontrackConfigProperties: OntrackConfigProperties,
) : SearchIndexer<FindingSearchItem> {

    private val logger: Logger = LoggerFactory.getLogger(FindingSearchIndexer::class.java)

    override val indexerName: String = "Security findings"

    override val indexName: String = FINDING_SEARCH_INDEX

    override val searchResultType = SearchResultType(
        feature = extensionFeature.featureDescription,
        id = FINDING_SEARCH_RESULT_TYPE,
        name = "Security finding",
        description = "External ID of a security finding, like a CVE or the ID of a scanner rule",
        order = SearchResultType.ORDER_PROPERTIES + 70,
    )

    override fun initIndex(builder: CreateIndexRequest.Builder): CreateIndexRequest.Builder =
        builder.mappings { mappings ->
            mappings
                .keyword(FindingSearchItem::externalId)
                .id(FindingSearchItem::projectId)
        }

    /**
     * An external ID is an identifier: it is matched as a whole, or by its beginning, never by
     * its parts — `CVE-2021-44228` must not find every other `CVE-2021-…`. The whole match comes
     * first.
     */
    override fun buildQuery(q: Query.Builder, token: String): ObjectBuilder<Query> =
        q.bool { b ->
            b
                .should { s ->
                    s.term { term ->
                        term.field(FindingSearchItem::externalId.name)
                            .value(token)
                            .caseInsensitive(true)
                            .boost(EXACT_MATCH_BOOST)
                    }
                }
                .should { s ->
                    s.prefix { prefix ->
                        prefix.field(FindingSearchItem::externalId.name)
                            .value(token)
                            .caseInsensitive(true)
                    }
                }
        }

    override fun indexAll(processor: (FindingSearchItem) -> Unit) {
        findingRepository.forEachFinding { finding ->
            processor(FindingSearchItem(finding))
        }
    }

    /**
     * Indexes findings which have just been created.
     *
     * A failure is logged and does not fail the caller: search must never block the ingestion
     * of a scan. A reindexation of the findings repairs the index.
     */
    fun indexFindings(findings: Collection<Finding>) {
        if (findings.isEmpty()) return
        try {
            findings.chunked(ontrackConfigProperties.search.index.batch).forEach { batch ->
                searchIndexService.batchSearchIndex(
                    indexer = this,
                    items = batch.map { FindingSearchItem(it) },
                    mode = BatchIndexMode.UPDATE,
                )
            }
        } catch (any: Exception) {
            logger.error("[search][findings] Cannot index ${findings.size} findings", any)
        }
    }

    override fun toSearchResult(id: String, score: Double, source: JsonNode): SearchResult? {
        val finding = id.toIntOrNull()?.let { findingQueryService.findFindingById(it) }
            ?: return null
        val project = structureService.findProjectByID(ID.of(finding.projectId))
            ?: return null
        val today = Time.now.toLocalDate()
        val branches = findingQueryService.getFindingExposures(finding)
            .groupBy { it.branch.id() }
            .mapNotNull { (_, exposures) ->
                val branch = exposures.first().branch
                FindingExposureState.of(exposures.map { it.exposure.stateOn(today) })
                    ?.takeIf { it != FindingExposureState.RESOLVED }
                    ?.let { state ->
                        FindingSearchResultBranch(id = branch.id(), name = branch.name, state = state)
                    }
            }
            .sortedBy { it.name }
        return SearchResult(
            title = finding.externalId,
            description = finding.title,
            accuracy = score,
            type = searchResultType,
            data = mapOf(
                SEARCH_RESULT_FINDING to FindingSearchResultFinding(
                    id = finding.id,
                    externalId = finding.externalId,
                    scanner = finding.scanner,
                    location = finding.location,
                    kind = finding.kind.name,
                    title = finding.title,
                    maxSeverity = finding.maxSeverity.name,
                    state = findingQueryService.getFindingState(finding, today)?.name,
                ),
                SearchResult.SEARCH_RESULT_PROJECT to project,
                SEARCH_RESULT_BRANCHES to branches,
            )
        )
    }

    companion object {
        const val SEARCH_RESULT_FINDING = "finding"
        const val SEARCH_RESULT_BRANCHES = "branches"
    }
}

/**
 * Index of the findings.
 */
const val FINDING_SEARCH_INDEX = "findings"

/**
 * Search result type of the findings.
 */
const val FINDING_SEARCH_RESULT_TYPE = "finding"

/**
 * Indexed finding.
 *
 * @property id ID of the finding
 * @property externalId External ID of the finding
 * @property projectId ID of the project of the finding, not searched
 */
data class FindingSearchItem(
    override val id: String,
    val externalId: String,
    val projectId: Int,
) : SearchItem {

    constructor(finding: Finding) : this(
        id = finding.id.toString(),
        externalId = finding.externalId,
        projectId = finding.projectId,
    )

    override val fields: Map<String, Any?> = mapOf(
        "externalId" to externalId,
        "projectId" to projectId,
    )
}

/**
 * Finding of a search result, what the result needs to be displayed and linked.
 */
data class FindingSearchResultFinding(
    val id: Int,
    val externalId: String,
    val scanner: String,
    val location: String,
    val kind: String,
    val title: String,
    val maxSeverity: String,
    val state: String?,
)

/**
 * Branch a found finding is exposed on, accepted or not.
 */
data class FindingSearchResultBranch(
    val id: Int,
    val name: String,
    val state: FindingExposureState,
)
