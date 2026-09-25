package net.nemerosa.ontrack.extension.scm.search

import net.nemerosa.ontrack.extension.scm.SCMExtensionConfigProperties
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.job.Schedule
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.model.support.StorageService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Search documents for the SCM commits: one per commit and project, found by its hash and short
 * hash (identifiers) and by the words of its message (text, truncated to [MAX_TEXT_BYTES]).
 *
 * The commits are not in the database and change outside of any transaction, so their documents
 * are written by scans of the SCMs:
 *
 * - an **incremental scan** ([indexNewCommits], run by [ScmCommitSearchJobs], hourly by default)
 *   scans only the commits after the last one indexed for each project, and inserts the documents
 *   which do not exist yet (`INSERT … ON CONFLICT DO NOTHING`);
 * - a **full scan** ([indexAll], the reconciliation of the type, weekly) rewrites the documents of
 *   all the commits and deletes those of the commits which are gone. It is what catches up on the
 *   commits an incremental scan missed: a failed write, a commit pushed with an older date than the
 *   last indexed one.
 *
 * A project whose SCM cannot list the commits after a given one is scanned fully every time, and
 * the existing documents are left untouched.
 *
 * The issues found in the messages are indexed by the same pass ([ScmIssueSearchExtension]).
 *
 * The last indexed commit of each project is kept in the [storage][StorageService].
 */
@Component
class ScmCommitSearchExtension(
    extensionFeature: SCMExtensionFeature,
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val ontrackConfigProperties: OntrackConfigProperties,
    scmExtensionConfigProperties: SCMExtensionConfigProperties,
    private val scmCommitSearchScanner: ScmCommitSearchScanner,
    private val scmIssueSearchExtension: ScmIssueSearchExtension,
    private val searchDocumentService: SearchDocumentService,
    private val storageService: StorageService,
) : AbstractExtension(extensionFeature), SearchDocumentIndexer, EventListener {

    private val logger: Logger = LoggerFactory.getLogger(ScmCommitSearchExtension::class.java)

    companion object {
        const val SCM_COMMIT_SEARCH_RESULT_TYPE = "scm-commit"
        const val SCM_COMMIT_SEARCH_RESULT_DATA_PROJECT = "project"

        /**
         * Maximum size of the indexed message of a commit, in UTF-8 bytes
         */
        const val MAX_TEXT_BYTES = 2048

        /**
         * Storage of the last indexed commit per project
         */
        private val STORE: String = ScmCommitSearchMarker::class.java.name

        /**
         * Key of the document of a commit. A commit ID is only unique within its repository, and
         * the same repository - or a fork, or a mirror - can be registered in several projects.
         */
        fun documentKey(project: Project, commit: String) = "${project.id()}::$commit"

        /**
         * Truncates a text to [MAX_TEXT_BYTES] bytes in UTF-8, without cutting a character, and
         * removes the NUL characters, which Postgres does not store in a text.
         */
        fun truncateText(text: String): String {
            val clean = text.replace("\u0000", "")
            val bytes = clean.toByteArray(Charsets.UTF_8)
            if (bytes.size <= MAX_TEXT_BYTES) {
                return clean
            }
            // Moving back to the first byte of the character being cut: the following bytes of a
            // UTF-8 character are 10xxxxxx
            var end = MAX_TEXT_BYTES
            while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) {
                end--
            }
            return String(bytes, 0, end, Charsets.UTF_8)
        }
    }

    override val searchResultType = SearchResultType(
        feature = extensionFeature.featureDescription,
        id = SCM_COMMIT_SEARCH_RESULT_TYPE,
        name = "SCM Commit",
        description = "Commit hash (abbreviated or not)",
        order = SearchResultType.ORDER_PROPERTIES + 60,
    )

    override val indexerName: String = "SCM Commits"

    /**
     * The full scan, weekly, unless the scheduled indexation of the commits is disabled.
     */
    override val indexerSchedule: Schedule =
        if (scmExtensionConfigProperties.search.scheduled) {
            Schedule.EVERY_WEEK
        } else {
            Schedule.NONE
        }

    private val traceCommits: Boolean
        get() = ontrackConfigProperties.search.index.logging &&
                ontrackConfigProperties.search.index.tracing &&
                logger.isDebugEnabled

    /**
     * Full scan of the commits of all the projects.
     */
    override fun indexAll(processor: (SearchDocument) -> Unit) {
        logger.debug("[search][indexation][scm-commits] Indexing all SCM commits")
        structureService.projectList.forEach { project ->
            try {
                scanProject(project, incremental = false, processor)
            } catch (any: Exception) {
                logger.error("[search][indexation][scm-commits] Cannot index commits for project ${project.name}", any)
                // The documents of this project are deleted by the rebuild, since they were not
                // provided: the next incremental scan of this project must be a full one.
                storageService.delete(STORE, project.id.toString())
            }
        }
    }

    /**
     * Incremental scan of the commits of all the projects.
     *
     * @return Number of scanned commits
     */
    fun indexNewCommits(): Int = securityService.asAdmin {
        structureService.projectList.sumOf { project ->
            try {
                indexNewCommits(project)
            } catch (any: Exception) {
                logger.error("[search][indexation][scm-commits] Cannot index new commits for project ${project.name}", any)
                0
            }
        }
    }

    /**
     * Incremental scan of the commits of a project: only the commits after the last indexed one
     * are scanned, and only the documents which do not exist yet are written.
     *
     * @return Number of scanned commits
     */
    fun indexNewCommits(project: Project): Int {
        val batchSize = ontrackConfigProperties.search.index.batch
        val buffer = mutableListOf<SearchDocument>()
        val flush = {
            if (buffer.isNotEmpty()) {
                searchDocumentService.insertIfAbsent(buffer.toList())
                buffer.clear()
            }
        }
        val count = scanProject(project, incremental = true) { document ->
            buffer += document
            if (buffer.size >= batchSize) {
                flush()
            }
        }
        flush()
        return count
    }

    private fun scanProject(project: Project, incremental: Boolean, processor: (SearchDocument) -> Unit): Int {
        val key = project.id.toString()
        val since = if (incremental) {
            storageService.find(STORE, key, ScmCommitSearchMarker::class)?.commit
        } else {
            null
        }
        val scan = scmCommitSearchScanner.scan(project, since) { commit ->
            if (traceCommits) {
                logger.debug("[search][indexation][scm-commits] project=${project.name} commit=${commit.shortId} message=${commit.message}")
            }
            processor(commit.asSearchDocument(project))
        } ?: return 0
        logger.debug(
            "[search][indexation][scm-commits] project=${project.name} incremental=${scan.incremental} commits=${scan.commits} issues=${scan.issueKeys.size}"
        )
        // Issues found in the messages
        if (scan.issueService != null && scan.issueKeys.isNotEmpty()) {
            scmIssueSearchExtension.indexIssues(project, scan.issueService, scan.issueKeys)
        }
        // Last indexed commit
        if (scan.lastCommit != null) {
            storageService.store(STORE, key, ScmCommitSearchMarker(scan.lastCommit))
        }
        return scan.commits
    }

    private fun SCMCommit.asSearchDocument(project: Project) = SearchDocument(
        type = SCM_COMMIT_SEARCH_RESULT_TYPE,
        key = documentKey(project, id),
        projectId = project.id(),
        entity = null,
        title = shortId,
        identifiers = listOf(id, shortId).distinct(),
        text = truncateText(message),
        data = mapOf(
            SCM_COMMIT_SEARCH_RESULT_DATA_PROJECT to project.searchDocumentData(),
            SearchResult.SEARCH_RESULT_ITEM to mapOf(
                "projectName" to project.name,
                "id" to id,
                "shortId" to shortId,
                "author" to author,
            ),
        ).asJson(),
        updatedAt = timestamp,
    )

    /**
     * The documents of a deleted project go with it: its last indexed commit too.
     */
    override fun onEvent(event: Event) {
        if (event.eventType == EventFactory.DELETE_PROJECT) {
            storageService.delete(STORE, event.getIntValue("PROJECT_ID").toString())
        }
    }

    /**
     * Last indexed commit of a project
     */
    data class ScmCommitSearchMarker(val commit: String)
}
