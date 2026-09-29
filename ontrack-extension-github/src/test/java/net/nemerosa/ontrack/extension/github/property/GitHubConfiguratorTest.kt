package net.nemerosa.ontrack.extension.github.property

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.git.model.GitConfiguration
import net.nemerosa.ontrack.extension.github.client.GitHubPR
import net.nemerosa.ontrack.extension.github.client.GitHubPRRef
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClient
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClientFactory
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GitHubConfiguratorTest {

    private lateinit var client: OntrackGitHubClient
    private lateinit var configurator: GitHubConfigurator
    private lateinit var configuration: GitHubGitConfiguration

    @BeforeEach
    fun setUp() {
        client = mockk()
        val clientFactory = mockk<OntrackGitHubClientFactory>()
        val engineConfiguration = GitHubEngineConfiguration(name = "github", url = null)
        every { clientFactory.create(engineConfiguration) } returns client
        configurator = GitHubConfigurator(
            propertyService = mockk(),
            issueServiceRegistry = mockk(),
            issueServiceExtension = mockk(),
            ontrackGitHubClientFactory = clientFactory,
            gitHubAppTokenService = mockk(),
        )
        configuration = GitHubGitConfiguration(
            property = GitHubProjectConfigurationProperty(
                configuration = engineConfiguration,
                repository = "yontrack/yontrack",
                indexationInterval = 0,
                issueServiceConfigurationIdentifier = null,
            ),
            configuredIssueService = null,
            authenticator = null,
        )
    }

    @Test
    fun `Pull request read from the GitHub PR`() {
        every { client.getPR("yontrack/yontrack", 12) } returns GitHubPR(
            number = 12,
            merged = false,
            state = "open",
            mergeable = true,
            mergeable_state = "clean",
            html_url = "https://github.com/yontrack/yontrack/pull/12",
            title = "Some feature",
            head = GitHubPRRef(ref = "feature/some"),
            base = GitHubPRRef(ref = "main"),
        )
        assertNotNull(configurator.getPullRequest(configuration, 12)) { pr ->
            assertEquals(12, pr.id)
            assertEquals("#12", pr.key)
            assertEquals("feature/some", pr.source)
            assertEquals("main", pr.target)
            assertEquals("Some feature", pr.title)
            assertEquals("open", pr.status)
            assertEquals("https://github.com/yontrack/yontrack/pull/12", pr.url)
        }
    }

    @Test
    fun `No pull request when GitHub does not know it`() {
        every { client.getPR("yontrack/yontrack", 404) } returns null
        assertNull(configurator.getPullRequest(configuration, 404))
    }

    @Test
    fun `No pull request when GitHub fails`() {
        every { client.getPR("yontrack/yontrack", 500) } throws IllegalStateException("GitHub is down")
        assertNull(configurator.getPullRequest(configuration, 500))
    }

    @Test
    fun `No pull request for a configuration which is not a GitHub one`() {
        assertNull(configurator.getPullRequest(mockk<GitConfiguration>(), 12))
    }

}
