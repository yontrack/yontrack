package net.nemerosa.ontrack.extension.bitbucket.cloud.client

import net.nemerosa.ontrack.extension.bitbucket.cloud.model.BitbucketCloudCommit
import net.nemerosa.ontrack.extension.bitbucket.cloud.model.BitbucketCloudMergeOutcome
import net.nemerosa.ontrack.extension.bitbucket.cloud.model.BitbucketCloudPullRequest
import net.nemerosa.ontrack.extension.bitbucket.cloud.model.BitbucketCloudRepository
import java.time.LocalDateTime

/**
 * Interface which defines how we talk to Bitbucket Cloud.
 */
interface BitbucketCloudClient {

    /**
     * Checks that the credentials are accepted by Bitbucket Cloud. Throws an exception if not.
     *
     * - API token: `GET /2.0/user`
     * - access token: `GET /2.0/hook_events` - `/2.0/user` is refused to access tokens, which have no user,
     *   while `/2.0/hook_events` needs no scope but rejects an invalid token with a 401.
     */
    fun validate()

    /**
     * Gets all repositories of a workspace.
     */
    fun getRepositories(workspace: String): List<BitbucketCloudRepository>

    /**
     * Given a [repository], returns its last modification date (if any).
     */
    fun getRepositoryLastModified(repository: BitbucketCloudRepository): LocalDateTime?

    /**
     * Given a [repository], returns its creation date (if any).
     */
    fun getRepositoryCreationDate(repository: BitbucketCloudRepository): LocalDateTime?

    /**
     * Gets the repository information.
     *
     * @param workspace Workspace slug
     * @param repository Repository slug
     */
    fun getRepository(workspace: String, repository: String): BitbucketCloudRepository

    /**
     * Last commit of a branch, `null` when the branch does not exist.
     *
     * `GET /2.0/repositories/{workspace}/{repository}/refs/branches/{branch}`
     */
    fun getBranchLastCommit(workspace: String, repository: String, branch: String): String?

    /**
     * Creates [newBranch] on the last commit of [sourceBranch] and returns this commit.
     *
     * `POST /2.0/repositories/{workspace}/{repository}/refs/branches`
     */
    fun createBranch(workspace: String, repository: String, sourceBranch: String, newBranch: String): String

    /**
     * Deletes a branch. A missing branch is ignored.
     *
     * `DELETE /2.0/repositories/{workspace}/{repository}/refs/branches/{branch}`
     */
    fun deleteBranch(workspace: String, repository: String, branch: String)

    /**
     * Content of a file at a commit or on a branch, `null` when not found.
     *
     * `GET /2.0/repositories/{workspace}/{repository}/src/{ref}/{path}`
     */
    fun download(workspace: String, repository: String, ref: String, path: String): ByteArray?

    /**
     * Commits a file on a branch, one commit per upload.
     *
     * `POST /2.0/repositories/{workspace}/{repository}/src` as a form
     */
    fun upload(
        workspace: String,
        repository: String,
        branch: String,
        path: String,
        content: ByteArray,
        message: String,
    )

    /**
     * Commits reachable from [toCommit] and not from [fromCommit], newest first, at most [maxCommits].
     *
     * `GET /2.0/repositories/{workspace}/{repository}/commits?include={toCommit}&exclude={fromCommit}`
     */
    fun getCommits(
        workspace: String,
        repository: String,
        fromCommit: String,
        toCommit: String,
        maxCommits: Int,
    ): List<BitbucketCloudCommit>

    /**
     * A commit, `null` when not found.
     *
     * `GET /2.0/repositories/{workspace}/{repository}/commit/{commit}`
     */
    fun getCommit(workspace: String, repository: String, commit: String): BitbucketCloudCommit?

    /**
     * A pull request, `null` when not found.
     *
     * `GET /2.0/repositories/{workspace}/{repository}/pullrequests/{id}`
     */
    fun getPullRequest(workspace: String, repository: String, id: Int): BitbucketCloudPullRequest?

    /**
     * Resolves reviewers to account UUIDs, in the same order. A reviewer between braces is already a UUID;
     * any other is looked up among the members of the workspace by account ID, nickname or display name.
     *
     * `GET /2.0/workspaces/{workspace}/members`, only when a reviewer is not a UUID
     */
    fun resolveReviewers(workspace: String, reviewers: List<String>): List<String>

    /**
     * Creates a pull request.
     *
     * `POST /2.0/repositories/{workspace}/{repository}/pullrequests`
     *
     * @param reviewers Account UUIDs of the reviewers
     */
    fun createPullRequest(
        workspace: String,
        repository: String,
        from: String,
        to: String,
        title: String,
        description: String,
        reviewers: List<String>,
    ): BitbucketCloudPullRequest

    /**
     * Approves a pull request as the identity of this client.
     *
     * `POST /2.0/repositories/{workspace}/{repository}/pullrequests/{id}/approve`
     */
    fun approvePullRequest(workspace: String, repository: String, id: Int)

    /**
     * States of the commit statuses (builds) of a pull request: `SUCCESSFUL`, `FAILED`, `INPROGRESS` or `STOPPED`.
     *
     * `GET /2.0/repositories/{workspace}/{repository}/pullrequests/{id}/statuses`
     */
    fun getPullRequestStatuses(workspace: String, repository: String, id: Int): List<String>

    /**
     * Tries to merge a pull request.
     *
     * `POST /2.0/repositories/{workspace}/{repository}/pullrequests/{id}/merge`
     *
     * @param strategy `merge_commit`, `squash` or `fast_forward`
     * @param closeSourceBranch Deleting the source branch once merged
     */
    fun mergePullRequest(
        workspace: String,
        repository: String,
        id: Int,
        strategy: String,
        message: String,
        closeSourceBranch: Boolean,
    ): BitbucketCloudMergeOutcome

}
