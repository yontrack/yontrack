package net.nemerosa.ontrack.extension.scm.search

import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommitFilter
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.model.structure.Project
import org.springframework.stereotype.Component

/**
 * The one pass over the commits of a project which feeds both the [commit][ScmCommitSearchExtension]
 * and the [issue][ScmIssueSearchExtension] search documents: the issues are extracted from the
 * messages of the commits as they are scanned.
 */
@Component
class ScmCommitSearchScanner(
    private val scmDetector: SCMDetector,
) {

    /**
     * Scans the commits of a project, oldest first.
     *
     * @param project Project whose commits are scanned
     * @param sinceCommit Only the commits after this one are scanned, when the SCM of the project
     * [supports it][SCMChangeLogEnabled.commitsSinceSupported]. All of them are scanned when it is
     * `null`, when the SCM does not support it, or when this commit is unknown.
     * @param code Called for each commit
     * @return Outcome of the scan, `null` when the project has no SCM able to list its commits
     */
    fun scan(project: Project, sinceCommit: String?, code: (SCMCommit) -> Unit): ScmCommitSearchScan? {
        val scm = scmDetector.getSCM(project) as? SCMChangeLogEnabled ?: return null
        val since = sinceCommit?.takeIf { scm.commitsSinceSupported }
        val issueService = scm.getConfiguredIssueService()
        val issueKeys = mutableSetOf<String>()
        var count = 0
        var lastCommit: String? = null
        scm.forAllCommits(
            project = project,
            filter = SCMCommitFilter(sinceCommit = since, sinceCommitTimestamp = null, count = Int.MAX_VALUE),
        ) { commit ->
            count++
            lastCommit = commit.id
            code(commit)
            if (issueService != null) {
                issueKeys += issueService.extractIssueKeysFromMessage(commit.message)
            }
        }
        return ScmCommitSearchScan(
            incremental = since != null,
            commits = count,
            lastCommit = lastCommit,
            issueService = issueService,
            issueKeys = issueKeys,
        )
    }

}

/**
 * Outcome of the scan of the commits of a project.
 *
 * @property incremental Whether only the commits after a given one were scanned
 * @property commits Number of scanned commits
 * @property lastCommit ID of the last scanned commit, the most recent one
 * @property issueService Issue service of the project, if any
 * @property issueKeys Keys of the issues found in the messages of the scanned commits
 */
data class ScmCommitSearchScan(
    val incremental: Boolean,
    val commits: Int,
    val lastCommit: String?,
    val issueService: ConfiguredIssueService?,
    val issueKeys: Set<String>,
)
