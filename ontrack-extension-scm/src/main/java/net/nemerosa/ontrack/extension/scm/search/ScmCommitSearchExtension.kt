package net.nemerosa.ontrack.extension.scm.search

import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.util.ObjectBuilder
import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.scm.SCMExtensionConfigProperties
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommitFilter
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.job.Schedule
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
class ScmCommitSearchExtension(
    extensionFeature: SCMExtensionFeature,
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val ontrackConfigProperties: OntrackConfigProperties,
    scmExtensionConfigProperties: SCMExtensionConfigProperties,
    private val scmDetector: SCMDetector,
    private val scmIssueSearchExtension: ScmIssueSearchExtension,
) : AbstractExtension(extensionFeature), SearchIndexer<ScmCommitSearchItem> {

    private val logger: Logger = LoggerFactory.getLogger(ScmCommitSearchExtension::class.java)

    companion object {
        const val SCM_COMMIT_SEARCH_RESULT_TYPE = "scm-commit"
        const val SCM_COMMIT_SEARCH_RESULT_DATA_PROJECT = "project"
        const val SCM_COMMIT_SEARCH_INDEX = "scm-commits"
    }

    override val indexerName: String = "SCM Commits"
    override val indexName: String = SCM_COMMIT_SEARCH_INDEX

    override val indexerSchedule: Schedule = scmExtensionConfigProperties.search.toSchedule()

    override fun initIndex(builder: CreateIndexRequest.Builder): CreateIndexRequest.Builder =
        builder.run {
            autoCompleteSettings()
        }.run {
            mappings { mappings ->
                mappings
                    .keyword(ScmCommitSearchItem::projectName)
                    .keyword(ScmCommitSearchItem::id)
                    .keyword(ScmCommitSearchItem::shortId)
                    .keyword(ScmCommitSearchItem::author)
                    .autoCompleteText(ScmCommitSearchItem::message)
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
                    ScmCommitSearchItem::id to 10.0,
                    ScmCommitSearchItem::shortId to 10.0,
                    ScmCommitSearchItem::projectName to 0.5,
                    ScmCommitSearchItem::author to 1.0,
                    ScmCommitSearchItem::message to 1.0,
                )
        }
    }

    override fun indexAll(processor: (ScmCommitSearchItem) -> Unit) {
        logger.debug("[search][indexation][scm-commits] Indexing all SCM commits")
        val traceCommits = ontrackConfigProperties.search.index.logging &&
                ontrackConfigProperties.search.index.tracing &&
                logger.isDebugEnabled
        securityService.asAdmin {
            structureService.projectList.forEach { project ->
                try {
                    indexProjectCommits(project, traceCommits, processor)
                } catch (any: Exception) {
                    logger.error("[search][indexation][scm-commits] Cannot index commits for project ${project.name}", any)
                }
            }
        }
    }

    private fun indexProjectCommits(
        project: Project,
        traceCommits: Boolean,
        processor: (ScmCommitSearchItem) -> Unit
    ) {
        val scm = scmDetector.getSCM(project)
        if (scm is SCMChangeLogEnabled) {
            logger.debug("[search][indexation][scm-commits] Indexing ${project.name} commits")
            var commitCount = 0
            val projectIssueKeys = mutableSetOf<String>()
            val issueConfig = scm.getConfiguredIssueService()
            scm.forAllCommits(project = project, filter = SCMCommitFilter.ALL) { commit ->
                commitCount++
                // Logging
                if (traceCommits) {
                    logger.debug("[search][indexation][scm-commits] project=${project.name} commit=${commit.shortId} message=${commit.message}")
                }
                // Indexation of the message
                val item = ScmCommitSearchItem(
                    projectName = project.name,
                    id = commit.id,
                    shortId = commit.shortId,
                    author = commit.author,
                    message = commit.message,
                )
                processor(item)
                // Indexes the list of issues for this commit
                if (issueConfig != null) {
                    val keys = issueConfig.extractIssueKeysFromMessage(commit.message)
                    projectIssueKeys.addAll(keys)
                }
            }
            // Processing of issues
            if (issueConfig != null && projectIssueKeys.isNotEmpty()) {
                logger.debug("[search][indexation][scm-commits] project=${project.name} issues=${projectIssueKeys.size} SCM issues have been found.")
                scmIssueSearchExtension.processIssueKeys(project, issueConfig, projectIssueKeys)
            }
        }
    }

    override val searchResultType = SearchResultType(
        feature = extensionFeature.featureDescription,
        id = SCM_COMMIT_SEARCH_RESULT_TYPE,
        name = "SCM Commit",
        description = "Commit hash (abbreviated or not)",
        order = SearchResultType.ORDER_PROPERTIES + 60,
    )

    override fun toSearchResult(
        id: String,
        score: Double,
        source: JsonNode
    ): SearchResult? {
        val item = source.parseOrNull<ScmCommitSearchItem>()
            ?: return null
        val project = structureService.findProjectByName(item.projectName)
            .getOrNull()
            ?.takeIf { securityService.isProjectFunctionGranted(it, ProjectView::class.java) }
            ?: return null
        return SearchResult(
            title = "[${project.name}] ${item.id}",
            description = "${item.author}: ${item.message}",
            accuracy = score,
            type = searchResultType,
            data = mapOf(
                SCM_COMMIT_SEARCH_RESULT_DATA_PROJECT to project,
                SearchResult.SEARCH_RESULT_ITEM to item,
            ),
        )
    }
}