package net.nemerosa.ontrack.extension.gitlab.client

import net.nemerosa.ontrack.extension.gitlab.model.GitLabBranch
import net.nemerosa.ontrack.extension.gitlab.model.GitLabCommit
import net.nemerosa.ontrack.extension.gitlab.model.GitLabIssue
import net.nemerosa.ontrack.extension.gitlab.model.GitLabMergeRequest
import net.nemerosa.ontrack.extension.gitlab.model.GitLabProject
import net.nemerosa.ontrack.extension.gitlab.model.GitLabUser

/**
 * Client used to connect to a GitLab instance from Yontrack.
 *
 * Everywhere a project is named, it is by its **full path**, subgroups included -
 * `group/subgroup/project` - which the client URL-encodes at call time.
 */
interface GitLabClient {

    /**
     * Checks the configuration can be used: fails when the instance cannot be reached or the token is
     * refused.
     */
    fun validate()

    /**
     * The user the configuration's token belongs to.
     *
     * It is what [validate] reads, and it is also the identity which opens, approves and merges the
     * auto-versioning merge requests - GitLab does not forbid self-approval, so there is no second one.
     */
    fun getCurrentUser(): GitLabUser

    /**
     * Projects this client is a member of.
     */
    fun getProjects(): List<GitLabProject>

    /**
     * Gets an issue of a project.
     *
     * @param project Full path of the project, like `group/subgroup/project`
     * @param iid Number of the issue **inside the project** - the one a `#123` reference names
     * @return The issue, or `null` when there is no such issue
     */
    fun getIssue(project: String, iid: Int): GitLabIssue?

    /**
     * Gets a merge request of a project.
     *
     * @param project Full path of the project, like `group/subgroup/project`
     * @param iid Number of the merge request **inside the project**
     * @return The merge request, or `null` when there is no such merge request
     */
    fun getMergeRequest(project: String, iid: Int): GitLabMergeRequest?

    /**
     * Creates a merge request.
     *
     * `approvals_before_merge` is deliberately **not** sent: it is deprecated since 16.0, and approval
     * rules are a Premium feature this module may not depend on.
     *
     * @param project Full path of the project
     * @param sourceBranch Branch holding the changes
     * @param targetBranch Branch to merge them into
     * @param title Title of the merge request
     * @param description Description (body) of the merge request
     * @param reviewerIds Numeric ids of the users to set as reviewers - see [findUserByUsername]
     * @param removeSourceBranch Delete the source branch once the merge request is merged
     * @param squash Ask for the commits to be squashed on merge. GitLab may override it, which is why
     * [GitLabMergeRequest.squash_on_merge] is what the answer is read back from.
     * @return The merge request GitLab created
     */
    fun createMergeRequest(
        project: String,
        sourceBranch: String,
        targetBranch: String,
        title: String,
        description: String,
        reviewerIds: List<Long>,
        removeSourceBranch: Boolean,
        squash: Boolean,
    ): GitLabMergeRequest

    /**
     * Approves a merge request as the configuration's own token.
     *
     * `POST /projects/:id/merge_requests/:iid/approve` is a **Free** endpoint - only approval *rules* are
     * Premium - and GitLab does not forbid self-approval: whether the author may approve is the project
     * setting `merge_requests_author_approval`.
     *
     * @param project Full path of the project
     * @param iid Number of the merge request inside the project
     */
    fun approveMergeRequest(project: String, iid: Int)

    /**
     * Merges a merge request, or asks GitLab to merge it on its own once everything passes.
     *
     * @param project Full path of the project
     * @param iid Number of the merge request inside the project
     * @param sha Head of the source branch, as [GitLabMergeRequest.sha] gives it. **Always sent**: GitLab
     * 19.2 added a project setting making it mandatory, and a mismatch answers 409 rather than merging
     * something else.
     * @param message Commit message for the merge
     * @param squash Squash the commits on merge
     * @param removeSourceBranch Delete the source branch after the merge
     * @param autoMerge `true` to set `auto_merge`, which merges when everything passes rather than now.
     * Not `merge_when_pipeline_succeeds`, deprecated in 17.11 - note the cancel endpoint kept the old name.
     * @return The merge request as GitLab answers it
     */
    fun mergeMergeRequest(
        project: String,
        iid: Int,
        sha: String,
        message: String,
        squash: Boolean,
        removeSourceBranch: Boolean,
        autoMerge: Boolean,
    ): GitLabMergeRequest

    /**
     * Gets a user by its exact username, to turn the reviewers of an auto-versioning configuration - which
     * are names - into the numeric `reviewer_ids` the merge request API takes.
     *
     * @param username Username, like `some-bot`, without any `@`
     * @return The user, or `null` when there is no such user
     */
    fun findUserByUsername(username: String): GitLabUser?

    /**
     * Gets the most recent commit of a project whose message names an issue.
     *
     * GitLab has no "commits of an issue" endpoint - the issue's own timeline is a Premium feature - so
     * this searches the project's commits for the `#123` reference, as the GitHub client searches for it
     * through the commit search API.
     *
     * @param project Full path of the project, like `group/subgroup/project`
     * @param iid Number of the issue **inside the project**
     * @return Full SHA of the most recent commit naming the issue, or `null` when there is none
     */
    fun getIssueLastCommit(project: String, iid: Int): String?

    /**
     * Gets a project by its full path.
     *
     * @param project Full path of the project, like `group/subgroup/project`
     * @return The project, or `null` when there is no such project or the token cannot see it
     */
    fun getProject(project: String): GitLabProject?

    /**
     * Gets a branch of a project.
     *
     * @param project Full path of the project
     * @param branch Simple name of the branch, like `main` and not `refs/heads/main`
     * @return The branch, or `null` when there is no such branch
     */
    fun getBranch(project: String, branch: String): GitLabBranch?

    /**
     * Full SHA of the head of a branch, or `null` when the branch does not exist.
     */
    fun getBranchLastCommit(project: String, branch: String): String?

    /**
     * Creates a branch off another one.
     *
     * @param project Full path of the project
     * @param sourceBranch Branch, tag or commit to branch off
     * @param newBranch Name of the branch to create
     * @return Full SHA of the head of the new branch
     */
    fun createBranch(project: String, sourceBranch: String, newBranch: String): String

    /**
     * Deletes a branch. A branch which does not exist is not an error.
     */
    fun deleteBranch(project: String, branch: String)

    /**
     * Downloads the raw content of a file.
     *
     * @param project Full path of the project
     * @param ref Branch, tag or commit to read the file at
     * @param path Path of the file inside the repository
     * @param retryOnNotFound Retry, for a bounded while, when the file is not found. Auto-versioning reads
     * a file on a branch it has just created, and GitLab can answer 404 for a moment after a branch is
     * created; anything else wants the missing file reported at once.
     * @return Content of the file, or `null` when there is no such file or ref
     */
    fun download(project: String, ref: String, path: String, retryOnNotFound: Boolean = false): ByteArray?

    /**
     * Creates or replaces a file on a branch.
     *
     * A replacement sends GitLab the file's `last_commit_id`, so that a file changed between the read and
     * the write is answered with a 409 rather than silently overwritten. That matters for auto-versioning,
     * which is the workload most likely to have two orders writing the same file at once.
     *
     * @param project Full path of the project
     * @param branch Branch to commit on
     * @param path Path of the file inside the repository
     * @param content Content of the file
     * @param message Commit message
     */
    fun upload(project: String, branch: String, path: String, content: ByteArray, message: String)

    /**
     * Commits between two references, as `GET /projects/:id/repository/compare` returns them.
     *
     * The comparison is run with `straight=false`, which is GitLab's `from...to`: the commits reachable from
     * [toRef] and not from the **merge base** of the two. That is what a change log wants, and what GitHub's
     * and Bitbucket Cloud's comparisons give.
     *
     * @param project Full path of the project
     * @param fromRef Starting reference, excluded
     * @param toRef Ending reference, included
     * @param maxCommits Maximum number of commits to return
     * @return Commits, most recent first, or an empty list when the range is empty or reversed
     */
    fun getCommits(project: String, fromRef: String, toRef: String, maxCommits: Int): List<GitLabCommit>

    /**
     * Gets a single commit by its SHA.
     *
     * @param project Full path of the project
     * @param commit SHA of the commit, full or abbreviated
     * @return The commit, or `null` when there is no such commit
     */
    fun getCommit(project: String, commit: String): GitLabCommit?

}
