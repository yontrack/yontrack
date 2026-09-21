package net.nemerosa.ontrack.extension.scm.search

import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.util.ObjectBuilder
import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.parseOrNull
import net.nemerosa.ontrack.model.security.ProjectView
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
class ScmIssueSearchExtension(
    extensionFeature: SCMExtensionFeature,
    private val securityService: SecurityService,
    private val structureService: StructureService,
    private val scmDetector: SCMDetector,
    private val ontrackConfigProperties: OntrackConfigProperties,
    private val searchIndexService: SearchIndexService,
) : AbstractExtension(extensionFeature), SearchIndexer<ScmIssueSearchItem> {

    private val logger: Logger = LoggerFactory.getLogger(ScmIssueSearchExtension::class.java)

    fun processIssueKeys(
        project: Project,
        issueConfig: ConfiguredIssueService,
        projectIssueKeys: Set<String>,
    ) {
        // Batch size
        val batchSize = ontrackConfigProperties.search.index.batch
        // Split the keys in batches
        val chunks = projectIssueKeys.chunked(batchSize)
        // For each batch
        chunks.forEach { batch ->
            logger.info("[search][indexation][scm-issues] project=${project.name} batch=${batch.size} Git issues to index.")
            searchIndexService.batchSearchIndex(
                indexer = this,
                items = batch.map { key ->
                    key to issueConfig.getDisplayKey(key)
                }.map { (key, displayKey) ->
                    ScmIssueSearchItem(project.name, key, displayKey)
                },
                mode = BatchIndexMode.KEEP
            )
        }
    }

    companion object {
        const val SCM_ISSUE_SEARCH_RESULT_TYPE = "scm-issue"
        const val SCM_ISSUE_SEARCH_RESULT_DATA_PROJECT = "project"
        const val SCM_ISSUE_SEARCH_INDEX = "scm-issues"
    }

    override val indexerName: String = "SCM Issues"
    override val indexName: String = SCM_ISSUE_SEARCH_INDEX

    override fun initIndex(builder: CreateIndexRequest.Builder): CreateIndexRequest.Builder =
        builder.run {
            mappings { mappings ->
                mappings
                    .keyword(ScmIssueSearchItem::key)
                    .keyword(ScmIssueSearchItem::displayKey)
            }
        }

    override fun buildQuery(
        q: Query.Builder,
        token: String
    ): ObjectBuilder<Query> {
        return q.multiMatch { m ->
            m.query(token)
                .type(TextQueryType.BestFields)
                .fields(
                    ScmIssueSearchItem::displayKey to 3.0,
                    ScmIssueSearchItem::key to 2.0,
                )
        }
    }

    /**
     * No indexation is needed - it's performed by the [ScmCommitSearchExtension].
     *
     * @see processIssueKeys
     */
    override fun indexAll(processor: (ScmIssueSearchItem) -> Unit) {}

    override val searchResultType = SearchResultType(
        feature = extensionFeature.featureDescription,
        id = SCM_ISSUE_SEARCH_RESULT_TYPE,
        name = "SCM Issue",
        description = "Issue key, as present in commit messages",
        order = SearchResultType.ORDER_PROPERTIES + 30,
    )

    override fun toSearchResult(
        id: String,
        score: Double,
        source: JsonNode
    ): SearchResult? {
        val item = source.parseOrNull<ScmIssueSearchItem>()
            ?: return null
        val project = structureService.findProjectByName(item.projectName)
            .getOrNull()
            ?.takeIf { securityService.isProjectFunctionGranted(it, ProjectView::class.java) }
            ?: return null
        val scm = scmDetector.getSCM(project)
            ?: return null
        if (scm is SCMChangeLogEnabled && scm.getConfiguredIssueService() != null) {
            return SearchResult(
                title = "Issue ${item.displayKey}",
                description = "Issue ${item.displayKey} found in project ${project.name}",
                accuracy = score,
                type = searchResultType,
                data = mapOf(
                    SCM_ISSUE_SEARCH_RESULT_DATA_PROJECT to project,
                    SearchResult.SEARCH_RESULT_ITEM to item
                )
            )
        } else {
            return null
        }
    }
}