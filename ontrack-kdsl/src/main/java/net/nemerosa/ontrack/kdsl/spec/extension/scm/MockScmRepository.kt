package net.nemerosa.ontrack.kdsl.spec.extension.scm

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLMissingDataException
import net.nemerosa.ontrack.kdsl.connector.graphql.checkData
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.MockScmDeleteRepositoryMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.MockScmRegisterCommitMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.MockScmRegisterIssueMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ProjectScmCommitAuthorQuery
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.connector.parse
import net.nemerosa.ontrack.kdsl.spec.Branch
import net.nemerosa.ontrack.kdsl.spec.Build
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.Project
import org.springframework.web.client.HttpClientErrorException.NotFound
import java.util.*

/**
 * Client for the mock SCM, whose repositories, commits and issues are registered over REST
 * rather than through GraphQL — see `MockSCMController` on the server side.
 *
 * It lives here, in the KDSL, rather than in the acceptance tests, because the demo seed
 * needs it too: registering the commits behind the demo's change log is the same set of
 * calls. The assertions written on top of it stay in the acceptance tests, which is where
 * their timeouts and their JUnit failures belong.
 *
 * Registering commits and issues, and emptying a repository, go through **GraphQL**; files,
 * branches and pull requests go through the REST endpoints of `MockSCMController`. The split
 * is not aesthetic: a deployed instance is reached through an ingress routing `/graphql` and
 * `/hook` to the backend and everything else to the Next UI, so the REST endpoints answer 404
 * from anywhere but inside the cluster. What the demo seed needs is what had to move.
 *
 * Either way the mock SCM has to be enabled with `ontrack.config.extension.scm.mock.enabled`:
 * without it neither the mutations nor the endpoints exist.
 */
fun <T> withMockScmRepository(
    ontrack: Ontrack,
    prefix: String = "ontrack-auto-versioning-test",
    code: MockScmRepositoryContext.() -> T,
): T {
    // Unique name for the repository
    val uuid = UUID.randomUUID().toString()
    val repo = "${prefix}-$uuid"

    // Context
    val context = MockScmRepositoryContext(ontrack, repo)

    // Running the code
    return context.code()
}

class MockScmRepositoryContext(
    private val ontrack: Ontrack,
    private val repository: String,
) {

    fun repositoryFile(
        path: String,
        branch: String = "main",
        content: () -> String,
    ) {
        ontrack.connector.post(
            "/extension/scm/mock/file",
            body = mapOf(
                "name" to repository,
                "scmBranch" to branch,
                "path" to path,
                "content" to content(),
            )
        )
    }

    /**
     * Declaring a new issue in the linked mock issue service.
     *
     * @param type Issue type, which is what a change log groups its issues by. Optional:
     * an issue with no type lands in the change log's untyped group.
     */
    fun repositoryIssue(key: String, message: String, type: String? = null) {
        ontrack.graphqlConnector.mutate(
            MockScmRegisterIssueMutation(
                repository = repository,
                key = key,
                message = message,
                type = Optional.presentIfNotNull(type),
            )
        ) {
            it?.mockScmRegisterIssue?.payloadUserErrors?.convert()
        }
    }

    /**
     * Deletes the repository and everything it holds.
     *
     * The mock SCM keeps its repositories on the server, where they outlive the entities
     * pointing at them, so anything re-registering the same commits starts here.
     */
    fun deleteRepository() {
        ontrack.graphqlConnector.mutate(
            MockScmDeleteRepositoryMutation(repository = repository)
        ) {
            it?.mockScmDeleteRepository?.payloadUserErrors?.convert()
        }
    }

    /**
     * Registering a commit in the repository.
     *
     * @return The id of the registered commit, deterministic for a given branch and
     * position on it: `<branch>-<index>-<sha1 prefix>`.
     *
     * @param author Name of the author of the commit, `unknown` if not set
     * @param authorEmail Email of the author of the commit
     */
    fun repositoryCommit(
        message: String,
        branch: String = "main",
        author: String? = null,
        authorEmail: String? = null,
    ): String =
        ontrack.graphqlConnector.mutate(
            MockScmRegisterCommitMutation(
                repository = repository,
                scmBranch = branch,
                message = message,
                author = Optional.presentIfNotNull(author),
                authorEmail = Optional.presentIfNotNull(authorEmail),
            )
        ) {
            it?.mockScmRegisterCommit?.payloadUserErrors?.convert()
        }
            ?.checkData { it.mockScmRegisterCommit?.commit }
            ?.id
            ?: throw GraphQLMissingDataException("Did not get back the registered commit")

    /**
     * Registering a commit in the repository and declaring it for the current build.
     */
    fun Build.withRepositoryCommit(message: String, property: Boolean = true): Build {
        // Declaring the commit first
        val commitId = repositoryCommit(message)
        // Setting the Git commit property for this build
        if (property) {
            // We're using the MOCK SCM, so a MOCK SCM Commit property must be set instead
            mockScmBuildCommitProperty = commitId
        }
        // OK
        return this
    }

    /**
     * Author of a commit, as the project reads it from its SCM.
     */
    fun Project.commitAuthor(commitId: String): MockSCMCommitAuthor =
        ontrack.graphqlConnector.query(ProjectScmCommitAuthorQuery(project = name, commitId = commitId))
            ?.projects?.firstOrNull()
            ?.scmCommitInfo?.scmDecoratedCommit?.commit
            ?.let { MockSCMCommitAuthor(name = it.author, email = it.authorEmail) }
            ?: throw GraphQLMissingDataException("Did not get back the commit $commitId")

    fun Project.configuredForMockScm() {
        mockScmProjectProperty = repository
    }

    fun Branch.configuredForMockRepository(
        scmBranch: String = "main",
    ) {
        project.configuredForMockScm()
        mockScmBranchProperty = scmBranch
    }

    fun getPR(from: String?, to: String?): MockSCMPullRequest? {
        var url = "/extension/scm/mock/pr?repository=$repository"
        if (from != null) {
            url += "&from=$from"
        }
        if (to != null) {
            url += "&to=$to"
        }
        return try {
            ontrack.connector.get(url).body.parse<MockSCMPullRequest>()
        } catch (_: NotFound) {
            null
        }
    }

    fun getFile(path: String, branch: String): String? =
        try {
            ontrack.connector.get("/extension/scm/mock/file?repository=$repository&scmBranch=$branch&path=$path")
                .body.parse<MockSCMFileContent>()
                .text
        } catch (_: NotFound) {
            null
        }

    fun getBranch(branch: String): MockSCMBranch? =
        try {
            ontrack.connector.get("/extension/scm/mock/branch?repository=$repository&scmBranch=$branch").body.parse()
        } catch (_: NotFound) {
            null
        }

    data class MockSCMPullRequest(
        val from: String,
        val to: String,
        val id: Int,
        val title: String,
        val body: String,
        val approved: Boolean,
        val status: String,
        val reviewers: List<String>,
    )

    data class MockSCMCommitAuthor(
        val name: String,
        val email: String?,
    )

    data class MockSCMFileContent(
        val text: String,
    )

    data class MockSCMBranch(
        val name: String,
    )

}
