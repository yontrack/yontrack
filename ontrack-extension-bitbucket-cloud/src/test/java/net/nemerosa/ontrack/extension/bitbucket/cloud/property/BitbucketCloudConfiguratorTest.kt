package net.nemerosa.ontrack.extension.bitbucket.cloud.property

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigMock
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClient
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import net.nemerosa.ontrack.extension.bitbucket.cloud.model.*
import org.junit.jupiter.api.Test
import org.springframework.web.client.ResourceAccessException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BitbucketCloudConfiguratorTest {

    private val config = bitbucketCloudTestConfigMock()
    private val client = mockk<BitbucketCloudClient>()
    private val clientFactory = mockk<BitbucketCloudClientFactory>().apply {
        every { getBitbucketCloudClient(config) } returns client
    }
    private val configurator = BitbucketCloudConfigurator(
        propertyService = mockk(),
        issueServiceRegistry = mockk(),
        clientFactory = clientFactory,
    )
    private val gitConfiguration = BitbucketCloudGitConfiguration(
        BitbucketCloudProjectConfigurationProperty(
            configuration = config,
            workspace = "ws",
            repository = "repo",
            indexationInterval = 0,
            issueServiceConfigurationIdentifier = null,
        ),
        null
    )

    @Test
    fun `Pull request`() {
        every { client.getPullRequest("ws", "repo", 12) } returns BitbucketCloudPullRequest(
            id = 12,
            title = "Some PR",
            state = "OPEN",
            source = BitbucketCloudPullRequestEndpoint(BitbucketCloudBranchName("feature/one")),
            destination = BitbucketCloudPullRequestEndpoint(BitbucketCloudBranchName("main")),
            links = BitbucketCloudLinks(BitbucketCloudLink("https://bitbucket.org/ws/repo/pull-requests/12")),
        )
        assertNotNull(configurator.getPullRequest(gitConfiguration, 12)) { pr ->
            assertEquals(12, pr.id)
            assertEquals("#12", pr.key)
            assertEquals("feature/one", pr.source)
            assertEquals("main", pr.target)
            assertEquals("Some PR", pr.title)
            assertEquals("OPEN", pr.status)
            assertEquals("https://bitbucket.org/ws/repo/pull-requests/12", pr.url)
        }
    }

    @Test
    fun `Pull request not found`() {
        every { client.getPullRequest("ws", "repo", 404) } returns null
        assertNull(configurator.getPullRequest(gitConfiguration, 404))
    }

    @Test
    fun `Pull request when Bitbucket Cloud cannot be reached`() {
        every { client.getPullRequest("ws", "repo", 1) } throws ResourceAccessException("Timeout")
        assertNull(configurator.getPullRequest(gitConfiguration, 1))
    }
}
