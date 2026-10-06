package net.nemerosa.ontrack.extension.scm.search

import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Search documents for the issues found in the messages of the commits: one per issue and project,
 * found by its key and display key.
 *
 * The documents are written by the scans of the commits of the [ScmCommitSearchExtension], which
 * extract the issues from the messages as they go. The rebuild of this type scans all the commits
 * again, for their issues only.
 */
@Component
class ScmIssueSearchExtension(
    extensionFeature: SCMExtensionFeature,
    private val structureService: StructureService,
    private val ontrackConfigProperties: OntrackConfigProperties,
    private val scmCommitSearchScanner: ScmCommitSearchScanner,
    private val searchDocumentService: SearchDocumentService,
) : AbstractExtension(extensionFeature), SearchDocumentIndexer {

    private val logger: Logger = LoggerFactory.getLogger(ScmIssueSearchExtension::class.java)

    companion object {
        const val SCM_ISSUE_SEARCH_RESULT_TYPE = "scm-issue"
        const val SCM_ISSUE_SEARCH_RESULT_DATA_PROJECT = "project"

        /**
         * Key of the document of an issue in a project
         */
        fun documentKey(project: Project, key: String) = "${project.id()}::$key"
    }

    override val searchResultType = SearchResultType(
        feature = extensionFeature.featureDescription,
        id = SCM_ISSUE_SEARCH_RESULT_TYPE,
        name = "SCM Issue",
        description = "Issue key, as present in commit messages",
        order = SearchResultType.ORDER_PROPERTIES + 30,
    )

    override val indexerName: String = "SCM Issues"

    override val nameReferences: List<SearchDocumentReference> = projectSearchDocumentReferences(SCM_ISSUE_SEARCH_RESULT_DATA_PROJECT)

    /**
     * Writes the documents of issues found in the commits of a project.
     */
    fun indexIssues(
        project: Project,
        issueService: ConfiguredIssueService,
        issueKeys: Set<String>,
    ) {
        issueKeys.chunked(ontrackConfigProperties.search.index.batch).forEach { batch ->
            logger.debug("[search][indexation][scm-issues] project=${project.name} batch=${batch.size} issues to index.")
            searchDocumentService.index(
                batch.map { key -> issueDocument(project, issueService, key) }
            )
        }
    }

    /**
     * Replaces the documents of the issues of a project: the given issues are indexed, and any
     * other issue indexed for this project - one no longer named by its commits - is removed.
     *
     * @param project Project whose issues are indexed
     * @param issueService Issue service of the project, used to get the display keys
     * @param issueKeys All the issue keys named by the commits of the project
     */
    fun replaceProjectIssues(
        project: Project,
        issueService: ConfiguredIssueService,
        issueKeys: Set<String>,
    ) {
        indexIssues(project, issueService, issueKeys)
        val deleted = searchDocumentService.deleteForProjectExcept(
            type = SCM_ISSUE_SEARCH_RESULT_TYPE,
            projectId = project.id(),
            keys = issueKeys.map { key -> documentKey(project, key) },
        )
        if (deleted > 0) {
            logger.info("[search][indexation][scm-issues] project=${project.name} deleted=$deleted issues no longer referenced.")
        }
    }

    /**
     * Scans all the commits of all the projects, for their issues.
     */
    override fun indexAll(processor: (SearchDocument) -> Unit) {
        structureService.projectList.forEach { project ->
            try {
                val scan = scmCommitSearchScanner.scan(project, sinceCommit = null) {}
                if (scan?.issueService != null) {
                    scan.issueKeys.forEach { key ->
                        processor(issueDocument(project, scan.issueService, key))
                    }
                }
            } catch (any: Exception) {
                logger.error("[search][indexation][scm-issues] Cannot index issues for project ${project.name}", any)
            }
        }
    }

    private fun issueDocument(project: Project, issueService: ConfiguredIssueService, key: String): SearchDocument {
        val displayKey = issueService.getDisplayKey(key)
        return SearchDocument(
            type = SCM_ISSUE_SEARCH_RESULT_TYPE,
            key = documentKey(project, key),
            projectId = project.id(),
            entity = null,
            title = displayKey,
            identifiers = listOf(key, displayKey).distinct(),
            text = null,
            data = mapOf(
                SCM_ISSUE_SEARCH_RESULT_DATA_PROJECT to project.searchDocumentData(),
                SearchResult.SEARCH_RESULT_ITEM to mapOf(
                    "projectName" to project.name,
                    "key" to key,
                    "displayKey" to displayKey,
                ),
            ).asJson(),
        )
    }
}
