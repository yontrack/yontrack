package net.nemerosa.ontrack.extension.github.client.mock

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.git.model.GitPullRequest
import net.nemerosa.ontrack.extension.github.client.*
import net.nemerosa.ontrack.extension.github.model.*
import org.springframework.web.client.RestTemplate

class MockOntrackGitHubClient(
    private val configuration: GitHubEngineConfiguration
) : OntrackGitHubClient {

    override fun getRateLimit(): GitHubRateLimit? {
        TODO("Not yet implemented")
    }

    override fun getIssue(
        repository: String,
        id: Int
    ): GitHubIssue? {
        TODO("Not yet implemented")
    }

    override val organizations: List<GitHubUser>
        get() = TODO("Not yet implemented")

    override fun findRepositoriesByOrganization(organization: String): List<GitHubRepository> {
        TODO("Not yet implemented")
    }

    override fun createGitHubRestTemplate(token: String?): RestTemplate {
        TODO("Not yet implemented")
    }

    override fun <T> graphQL(
        message: String,
        query: String,
        variables: Map<String, *>,
        token: String?,
        code: (data: JsonNode) -> T
    ): T {
        TODO("Not yet implemented")
    }

    override fun getPR(
        repository: String,
        pr: Int
    ): GitHubPR {
        TODO("Not yet implemented")
    }

    @Deprecated("Will be removed in V6. Use getPR instead.")
    override fun getPullRequest(
        repository: String,
        id: Int,
        ignoreError: Boolean
    ): GitPullRequest? {
        TODO("Not yet implemented")
    }

    override fun getOrganizationTeams(login: String): List<GitHubTeam>? {
        TODO("Not yet implemented")
    }

    override fun getTeamRepositories(
        login: String,
        teamSlug: String
    ): List<GitHubTeamRepository>? {
        TODO("Not yet implemented")
    }

    override fun getRepositorySettings(
        repository: String,
        askVisibility: Boolean
    ): GitHubRepositorySettings {
        TODO("Not yet implemented")
    }

    override fun getFileContent(
        repository: String,
        branch: String?,
        path: String,
        retryOnNotFound: Boolean
    ): ByteArray? {
        TODO("Not yet implemented")
    }

    override fun getFile(
        repository: String,
        branch: String?,
        path: String,
        retryOnNotFound: Boolean
    ): GitHubFile? {
        TODO("Not yet implemented")
    }

    override fun getBranchLastCommit(
        repository: String,
        branch: String,
        retryOnNotFound: Boolean
    ): String? {
        TODO("Not yet implemented")
    }

    override fun deleteBranch(repository: String, branch: String) {
        TODO("Not yet implemented")
    }

    override fun createBranch(
        repository: String,
        source: String,
        destination: String
    ): String? {
        TODO("Not yet implemented")
    }

    override fun setFileContent(
        repository: String,
        branch: String,
        sha: String,
        path: String,
        content: ByteArray,
        message: String
    ) {
        TODO("Not yet implemented")
    }

    override fun createPR(
        repository: String,
        title: String,
        head: String,
        base: String,
        body: String,
        reviewers: List<String>
    ): GitHubPR {
        TODO("Not yet implemented")
    }

    override fun approvePR(repository: String, pr: Int, body: String, token: String?) {
        TODO("Not yet implemented")
    }

    override fun enableAutoMerge(repository: String, pr: Int, message: String) {
        TODO("Not yet implemented")
    }

    override fun isPRMergeable(repository: String, pr: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun mergePR(repository: String, pr: Int, message: String) {
        TODO("Not yet implemented")
    }

    override fun compareCommits(
        repository: String,
        base: String,
        head: String
    ): List<GitHubCommit> {
        TODO("Not yet implemented")
    }

    override fun getCommit(
        repository: String,
        commit: String
    ): GitHubCommit {
        val author = GitHubAuthor(
            name = "test",
            date = Time.now,
            email = "test@yontrack.com"
        )
        return GitHubCommit(
            sha = commit,
            url = "${configuration.url}/$repository/commit/$commit",
            commit = GitHubCommitInfo(
                author = author,
                committer = author,
                message = "Commit message",
            ),
            parents = null,
        )
    }

    override fun launchWorkflowRun(
        repository: String,
        workflow: String,
        branch: String,
        inputs: Map<String, String>,
        retries: Int,
        retriesDelaySeconds: Int
    ): WorkflowRun {
        TODO("Not yet implemented")
    }

    override fun getWorkflowRun(
        repository: String,
        runId: Long
    ): WorkflowRun {
        TODO("Not yet implemented")
    }

    override fun waitUntilWorkflowRun(
        repository: String,
        runId: Long,
        retries: Int,
        retriesDelaySeconds: Int
    ) {
        TODO("Not yet implemented")
    }

    override fun getIssueLastCommit(repository: String, key: Int): String? {
        TODO("Not yet implemented")
    }

    override fun forAllCommits(repository: String, code: (commit: GitHubCommit) -> Unit) {
    }

    override fun mergeBranch(repository: String, head: String, base: String): GitHubCommit? {
        TODO("Not yet implemented")
    }
}