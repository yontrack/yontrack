package net.nemerosa.ontrack.extension.github.client

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.git.model.GitPullRequest
import net.nemerosa.ontrack.extension.github.model.*
import org.springframework.web.client.RestTemplate


/**
 * Client used to connect to a GitHub engine from Ontrack.
 */
interface OntrackGitHubClient {

    /**
     * Gets the rate limits for the current authentication.
     */
    fun getRateLimit(): GitHubRateLimit?

    /**
     * Gets an issue from a repository.
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param id         ID of the issue
     * @return Details about the issue
     */
    fun getIssue(repository: String, id: Int): GitHubIssue?

    /**
     * Gets the list of organizations available from this client.
     */
    val organizations: List<GitHubUser>

    /**
     * Gets the list of repositories for an organization
     *
     * @param organization Organization name
     * @return List of repository in this [organization]
     */
    fun findRepositoriesByOrganization(organization: String): List<GitHubRepository>

    /**
     * Creates a [RestTemplate] for accessing GitHub using REST.
     *
     * This should be used exclusively for calling the REST API of GitHub. For GraphQL calls,
     * please use the [graphQL] function.
     *
     * @param token Alternative token
     *
     * @see graphQL
     */
    fun createGitHubRestTemplate(token: String? = null): RestTemplate

    /**
     * Performs a GraphQL call against GitHub.
     *
     * @param message Title for the call (used in Ontrack logs)
     * @param query GraphQL query
     * @param variables GraphQL variables
     * @param token Alternative token
     * @param code Code to run against the `data` node of the GraphQL response. Note that GraphQL level errors
     * have already been processed.
     * @return Response returns by [code]
     * @see createGitHubRestTemplate
     */
    fun <T> graphQL(
        message: String,
        query: String,
        variables: Map<String, *> = emptyMap<String, Any>(),
        token: String? = null,
        code: (data: JsonNode) -> T,
    ): T

    /**
     * Gets a pull request using its ID
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param id         ID of the pull request
     * @param ignoreError If `true` in case of error, this method returns `null`
     * @return Details of the pull request or `null` if it does not exist
     */
    @Deprecated("Will be removed in V6. Use getPR instead.")
    fun getPullRequest(repository: String, id: Int, ignoreError: Boolean = false): GitPullRequest?

    /**
     * Gets the list of teams for this organization.
     *
     * The `read:org` permission is required (it not granted, `null` will be returned).
     *
     * @param login Login for the organization.
     * @return List of teams or null if the teams cannot be accessed.
     */
    fun getOrganizationTeams(login: String): List<GitHubTeam>?

    /**
     * Returns the list of repositories associated to a team.
     *
     * The `read:org` permission is required (it not granted, `null` will be returned).
     *
     * @param login Login for the organization.
     * @param teamSlug Slug of the team
     * @return List of repositories with their permissions or null if the permissions cannot be accessed.
     */
    fun getTeamRepositories(login: String, teamSlug: String): List<GitHubTeamRepository>?

    /**
     * Returns the settings for a repository.
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param askVisibility True to ask the visibility (extra performance cost)
     * @return Settings
     */
    fun getRepositorySettings(repository: String, askVisibility: Boolean = false): GitHubRepositorySettings

    /**
     * Downloads a file on a branch.
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param branch Name of the branch (without refs/heads) or null if the default branch must be used
     * @param path Path to the file
     * @param retryOnNotFound If the file is not found, should we retry until a timeout is reached?
     * @return Binary content of the file or null if the file cannot be found
     */
    fun getFileContent(repository: String, branch: String?, path: String, retryOnNotFound: Boolean = false): ByteArray?

    /**
     * Downloads a file on a branch.
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param branch Name of the branch (without refs/heads) or null if the default branch must be used
     * @param path Path to the file
     * @param retryOnNotFound If `true`, if the file is not found, we assume it's not available yet, and we'll try several times
     * @return Raw content of the file or null if not found
     */
    fun getFile(repository: String, branch: String?, path: String, retryOnNotFound: Boolean = false): GitHubFile?

    /**
     * Gets the last commit for a branch.
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param branch Name of the branch
     * @param retryOnNotFound True if the call must be retried until something is found
     * @return SHA of the last commit of the branch, `null` if no commit or no branch
     */
    fun getBranchLastCommit(repository: String, branch: String, retryOnNotFound: Boolean = true): String?

    /**
     * Deletes a branch if it exists. Any linked PR will be declined first.
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param branch Name of the branch`
     */
    fun deleteBranch(repository: String, branch: String)

    /**
     * Creates a branch
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param source Source branch
     * @param destination Branch to create
     * @return SHA of the commit, `null` if no source branch
     */
    fun createBranch(repository: String, source: String, destination: String): String?

    /**
     * Sets the content of the file at [path].
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param branch Branch to target
     * @param sha Sha of the initial file
     * @param path Path to get the file at
     * @param content Content of the file as binary
     * @param message Commit message
     */
    fun setFileContent(
        repository: String,
        branch: String,
        sha: String,
        path: String,
        content: ByteArray,
        message: String
    )

    /**
     * Creates a pull request
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param title PR title
     * @param head Source branch
     * @param base Target branch
     * @param body PR description
     * @param reviewers List of reviewers
     * @return Created PR
     */
    fun createPR(
        repository: String,
        title: String,
        head: String,
        base: String,
        body: String,
        reviewers: List<String>,
    ): GitHubPR

    /**
     * Approves a pull request.
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param pr PR number
     * @param body PR approval description
     * @param token If defined, used for the authentication of this call
     */
    fun approvePR(repository: String, pr: Int, body: String, token: String?)

    /**
     * Enables auto merge on a pull request
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param pr PR number
     * @param message Message to use for the actual merge commit
     */
    fun enableAutoMerge(repository: String, pr: Int, message: String)

    /**
     * Checks if a pull request is mergeable
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param pr PR number
     * @return PR mergeable state
     */
    fun isPRMergeable(repository: String, pr: Int): Boolean

    /**
     * Gets a PR using its ID.
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param pr PR number
     * @return PR details
     */
    fun getPR(repository: String, pr: Int): GitHubPR?

    /**
     * Merges a PR.
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param pr PR number
     * @param message Commit message
     */
    fun mergePR(repository: String, pr: Int, message: String)

    /**
     * Gets a list of commits between a base and a head.
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param base Base commit
     * @param head Head commit
     */
    fun compareCommits(
        repository: String,
        base: String,
        head: String,
    ): List<GitHubCommit>

    /**
     * Gets information about a commit
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param commit Commit SHA
     * @return Commit information or null if not found
     */
    fun getCommit(
        repository: String,
        commit: String,
    ): GitHubCommit?

    /**
     * Launching a workflow run and getting its ID.
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param workflow Name of the workflow, like `my-workflow.yml`
     * @param branch Branch where to launch the workflow
     * @param inputs List of parameters to pass to the workflow
     * @param retries The number of times we check for successful scheduling
     * @param retriesDelaySeconds The time (in seconds) between two checks for successful scheduling
     * @return Launched workflow
     */
    fun launchWorkflowRun(
        repository: String,
        workflow: String,
        branch: String,
        inputs: Map<String, String>,
        retries: Int = 10,
        retriesDelaySeconds: Int = 30,
    ): WorkflowRun

    /**
     * Waits until a workflow completes.
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param runId ID of the workflow run
     * @param retries The amount of times we check for completion
     * @param retriesDelaySeconds The time (in seconds) between two checks for completion
     */
    fun waitUntilWorkflowRun(
        repository: String,
        runId: Long,
        retries: Int = 10,
        retriesDelaySeconds: Int = 30,
    )

    /**
     * Gets a workflow run by its ID.
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param runId ID of the workflow run
     * @return Workflow run information.
     */
    fun getWorkflowRun(repository: String, runId: Long): WorkflowRun

    /**
     * Gets the last commit for a given issue
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param key Issue key, like `1234`
     */
    fun getIssueLastCommit(repository: String, key: Int): String?

    /**
     * Iterates over all commits of a repository.
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param code Code to run for each commit
     */
    fun forAllCommits(repository: String, code: (commit: GitHubCommit) -> Unit)

    /**
     * Merges [head] branch into [base] branch using the GitHub Merges API.
     *
     * @param repository Repository name, like `nemerosa/ontrack`
     * @param head Source branch (merge FROM, e.g. the upgrade branch)
     * @param base Target branch (merge INTO, e.g. `main`)
     * @return The resulting merge commit, or null if already up-to-date (nothing to merge)
     */
    fun mergeBranch(repository: String, head: String, base: String): GitHubCommit?

    companion object {
        const val PROPERTY_GITHUB_CLIENT_TYPE = "ontrack.extension.github.client.type"
        const val PROPERTY_GITHUB_CLIENT_TYPE_DEFAULT = "default"
        const val PROPERTY_GITHUB_CLIENT_TYPE_MOCK = "mock"
    }

}