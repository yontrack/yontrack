package net.nemerosa.ontrack.extension.github.client

import io.micrometer.core.instrument.MeterRegistry
import io.mockk.mockk
import net.nemerosa.ontrack.extension.github.TestOnGitHub
import net.nemerosa.ontrack.extension.github.app.DefaultGitHubAppTokenService
import net.nemerosa.ontrack.extension.github.app.MockGitHubAppClient
import net.nemerosa.ontrack.extension.github.githubTestConfigReal
import net.nemerosa.ontrack.extension.github.githubTestEnv
import net.nemerosa.ontrack.extension.github.model.GitHubRepositoryPermission
import net.nemerosa.ontrack.git.support.GitConnectionConfig
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestOnGitHub
class DefaultOntrackGitHubClientIT {

    private lateinit var client: OntrackGitHubClient

    private val meterRegistry: MeterRegistry = mockk(relaxed = true)

    @BeforeEach
    fun init() {
        client = DefaultOntrackGitHubClient(
            configuration = githubTestConfigReal(),
            gitHubAppTokenService = DefaultGitHubAppTokenService(
                gitHubAppClient = MockGitHubAppClient(),
                ontrackConfigProperties = OntrackConfigProperties(),
            ),
            timeout = Duration.ofSeconds(60),
            meterRegistry = meterRegistry,
            gitConnectionConfig = GitConnectionConfig.default,
        )
    }

    @Test
    fun `Getting the list of organizations`() {
        val organizations = client.organizations
        assertTrue(
            organizations.any { it.login == githubTestEnv.organization },
            "Can get the list of organizations"
        )
    }

    @Test
    fun `Checking a non existing branch`() {
        val branch = uid("test-")
        val commit = client.getBranchLastCommit(
            repository = githubTestEnv.fullRepository,
            branch = branch,
            retryOnNotFound = false,
        )
        assertNull(commit, "Branch does not exist")
    }

    @Test
    fun `Creating and deleting a branch`() {
        val branch = uid("test-")
        // Existing HEAD commit
        val commit = client.getBranchLastCommit(
            repository = githubTestEnv.fullRepository,
            branch = githubTestEnv.branch,
        )
        assertNotNull(commit, "Head commit is defined")
        // Creating the branch
        client.createBranch(
            repository = githubTestEnv.fullRepository,
            source = githubTestEnv.branch,
            destination = branch,
        )
        // Checking the branch does exist
        val branchCommit = client.getBranchLastCommit(
            repository = githubTestEnv.fullRepository,
            branch = branch,
        )
        assertEquals(commit, branchCommit, "Branch does exist")
        // Deleting the branch
        client.deleteBranch(
            repository = githubTestEnv.fullRepository,
            branch = branch,
        )
        // Checking the branch does not exist any longer
        val deletedCommit = client.getBranchLastCommit(
            repository = githubTestEnv.fullRepository,
            branch = branch,
            retryOnNotFound = false,
        )
        assertEquals(null, deletedCommit, "Branch does not exist any longer")
    }

    @Test
    fun `Creating and deleting a branch with a PR`() {
        val branch = uid("test-")
        // Existing HEAD commit
        val commit = client.getBranchLastCommit(
            repository = githubTestEnv.fullRepository,
            branch = githubTestEnv.branch,
        )
        assertNotNull(commit, "Head commit is defined")
        // Creating the branch
        client.createBranch(
            repository = githubTestEnv.fullRepository,
            source = githubTestEnv.branch,
            destination = branch,
        )
        // Adding a commit
        val testFile = uid("file-")
        client.setFileContent(
            repository = githubTestEnv.fullRepository,
            branch = branch,
            sha = "",
            path = testFile,
            content = testFile.toByteArray(),
            message = "Adding $testFile",
        )
        // Creating a PR
        client.createPR(
            repository = githubTestEnv.fullRepository,
            title = "PR for $testFile",
            head = branch,
            base = githubTestEnv.branch,
            body = "Sample PR",
            reviewers = emptyList()
        )
        // Deleting the branch
        client.deleteBranch(
            repository = githubTestEnv.fullRepository,
            branch = branch,
        )
        // Checking the branch does not exist any longer
        val deletedCommit = client.getBranchLastCommit(
            repository = githubTestEnv.fullRepository,
            branch = branch,
            retryOnNotFound = false,
        )
        assertEquals(null, deletedCommit, "Branch does not exist any longer")
    }

    @Test
    fun `Getting the list of repositories for an organization`() {
        val repos = client.findRepositoriesByOrganization(githubTestEnv.organization)
        assertTrue(
            repos.any { it.name == githubTestEnv.repository },
            "Found the ${githubTestEnv.repository} repository in the ${githubTestEnv.organization} organization"
        )
    }

    @Test
    fun `Getting an issue`() {
        val issue = client.getIssue(githubTestEnv.fullRepository, githubTestEnv.issues.issue)
        assertNotNull(issue, "Issue ${githubTestEnv.fullRepository}#${githubTestEnv.issues.issue} has been found") {
            assertEquals("#${githubTestEnv.issues.issue}", it.displayKey)
        }
    }

    @Test
    fun `Getting an unknown issue`() {
        val id = Int.MAX_VALUE / 2
        val issue = client.getIssue(githubTestEnv.fullRepository, id)
        assertNull(issue, "Issue ${githubTestEnv.fullRepository}#$id cannot be found found")
    }

    @Test
    fun `Getting a PR`() {
        val pr = client.getPR(githubTestEnv.fullRepository, githubTestEnv.pr)
        assertNotNull(pr, "PR ${githubTestEnv.fullRepository}#${githubTestEnv.pr} has been found") {
            assertEquals(githubTestEnv.pr, it.number)
            assertNotNull(it.head?.ref, "PR source branch")
            assertNotNull(it.base?.ref, "PR target branch")
        }
    }

    @Test
    fun `Getting an unknown PR`() {
        val id = Int.MAX_VALUE / 2
        val pr = client.getPR(githubTestEnv.fullRepository, id)
        assertNull(pr, "Issue ${githubTestEnv.fullRepository}#$id cannot be found found")
    }

    @Test
    fun `Getting the teams for an organization`() {
        val teams = client.getOrganizationTeams(githubTestEnv.organization)
        assertNotNull(teams, "Getting the teams") { list ->
            assertTrue(
                list.any { it.slug == githubTestEnv.team },
                "Found the ${githubTestEnv.team} team in the ${githubTestEnv.organization} organization"
            )
        }
    }

    @Test
    fun `Getting the repositories for a team`() {
        val teamRepos = client.getTeamRepositories(githubTestEnv.organization, githubTestEnv.team)
        assertNotNull(teamRepos, "Getting the repositories for a team") { list ->
            val teamRepo = list.find {
                it.repository == githubTestEnv.repository
            }
            assertNotNull(teamRepo, "Found ${githubTestEnv.repository} in ${githubTestEnv.team} team") {
                assertEquals(GitHubRepositoryPermission.READ, it.permission)
            }
        }
    }

    @Test
    fun `Getting a list of commits for a change log`() {
        val commits = client.compareCommits(
            repository = githubTestEnv.fullRepository,
            base = githubTestEnv.changeLog.from,
            head = githubTestEnv.changeLog.to,
        )
        assertEquals(
            githubTestEnv.changeLog.messages,
            commits.map { it.commit.message }
        )
    }

    @Test
    fun `Getting the last commit for an issue`() {
        val commit = client.getIssueLastCommit(
            repository = githubTestEnv.fullRepository,
            key = githubTestEnv.issues.issue,
        )
        assertEquals(
            githubTestEnv.issues.issueCommit,
            commit,
        )
    }

}
