package net.nemerosa.ontrack.extension.bitbucket.cloud.scm

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClient
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.model.*
import net.nemerosa.ontrack.extension.bitbucket.cloud.settings.BitbucketCloudMergeStrategy
import net.nemerosa.ontrack.extension.bitbucket.cloud.settings.BitbucketCloudSettings
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequestStatus
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class BitbucketCloudSCMExtensionPullRequestTest {

    private val config = BitbucketCloudConfiguration(
        name = "config",
        authType = BitbucketCloudAuthType.API_TOKEN,
        email = "bot@example.com",
        token = "bot-token",
        autoMergeEmail = "approver@example.com",
        autoMergeToken = "approver-token",
    )

    /**
     * Configuration used to approve: the auto-merge identity, with an API token.
     */
    private val approverConfig = BitbucketCloudConfiguration(
        name = "config",
        authType = BitbucketCloudAuthType.API_TOKEN,
        email = "approver@example.com",
        token = "approver-token",
        autoMergeEmail = "approver@example.com",
        autoMergeToken = "approver-token",
    )

    private val noApproverConfig = config.copy(name = "no-approver", autoMergeEmail = null, autoMergeToken = null)

    private lateinit var client: BitbucketCloudClient
    private lateinit var approverClient: BitbucketCloudClient
    private lateinit var settings: BitbucketCloudSettings
    private lateinit var extension: BitbucketCloudSCMExtension

    @BeforeEach
    fun init() {
        client = mockk()
        approverClient = mockk()
        settings = BitbucketCloudSettings(autoMergeTimeout = 500, autoMergeInterval = 10)
        val clientFactory = mockk<BitbucketCloudClientFactory>()
        every { clientFactory.getBitbucketCloudClient(config) } returns client
        every { clientFactory.getBitbucketCloudClient(noApproverConfig) } returns client
        every { clientFactory.getBitbucketCloudClient(approverConfig) } returns approverClient
        val configurationService = mockk<BitbucketCloudConfigurationService>()
        every { configurationService.findConfiguration("config") } returns config
        every { configurationService.findConfiguration("no-approver") } returns noApproverConfig
        val cachedSettingsService = mockk<CachedSettingsService>()
        every { cachedSettingsService.getCachedSettings(BitbucketCloudSettings::class.java) } answers { settings }
        extension = BitbucketCloudSCMExtension(
            extensionFeature = mockk(),
            propertyService = mockk(),
            structureService = mockk(),
            clientFactory = clientFactory,
            cachedSettingsService = cachedSettingsService,
            configurationService = configurationService,
            issueServiceRegistry = mockk(),
            gitRepositoryClientFactory = mockk(),
            gitConfigService = mockk(),
            ontrackConfigProperties = mockk(),
        )

        every { client.resolveReviewers("ws", any()) } answers { secondArg<List<String>>().map { "{$it}" } }
        every { client.createPullRequest("ws", "repo", "feature/upgrade", "main", "Upgrade", "Description", any()) } returns pr("OPEN")
        every { client.getPullRequest("ws", "repo", 12) } returns pr("OPEN")
        every { approverClient.approvePullRequest("ws", "repo", 12) } returns Unit
    }

    private fun scm(configName: String = "config"): SCM =
        extension.getSCMPath(configName, "ws/repo/any")?.scm ?: fail("SCM path should have been found")

    private fun pr(state: String) = BitbucketCloudPullRequest(
        id = 12,
        title = "Upgrade",
        state = state,
        source = null,
        destination = null,
        links = BitbucketCloudLinks(html = BitbucketCloudLink(href = "https://bitbucket.org/ws/repo/pull-requests/12")),
    )

    private fun createPR(
        configName: String = "config",
        autoApproval: Boolean = true,
        remoteAutoMerge: Boolean = false,
        reviewers: List<String> = emptyList(),
    ) = scm(configName).createPR(
        from = "feature/upgrade",
        to = "main",
        title = "Upgrade",
        description = "Description",
        autoApproval = autoApproval,
        remoteAutoMerge = remoteAutoMerge,
        message = "Merge message",
        reviewers = reviewers,
    )

    private fun mergeWith(
        strategy: String = "squash",
        closeSourceBranch: Boolean = true,
    ) = client.mergePullRequest("ws", "repo", 12, strategy, "Merge message", closeSourceBranch)

    @Test
    fun `Pull request created with reviewers and without auto approval`() {
        val pr = createPR(autoApproval = false, reviewers = listOf("alice", "bob"))
        assertEquals("12", pr.id)
        assertEquals("PR-12", pr.name)
        assertEquals("https://bitbucket.org/ws/repo/pull-requests/12", pr.link)
        assertEquals(SCMPullRequestStatus.OPEN, pr.status)
        verify {
            client.createPullRequest(
                "ws", "repo", "feature/upgrade", "main", "Upgrade", "Description", listOf("{alice}", "{bob}")
            )
        }
        verify(exactly = 0) { approverClient.approvePullRequest(any(), any(), any()) }
        verify(exactly = 0) { client.mergePullRequest(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `Pull request approved with the auto merge identity and merged`() {
        every { client.getPullRequestStatuses("ws", "repo", 12) } returns listOf("SUCCESSFUL", "SUCCESSFUL")
        every { mergeWith() } returns BitbucketCloudMergeOutcome.MERGED
        val pr = createPR()
        assertEquals(SCMPullRequestStatus.MERGED, pr.status)
        verify { approverClient.approvePullRequest("ws", "repo", 12) }
        verify(exactly = 0) { client.approvePullRequest(any(), any(), any()) }
        verify(exactly = 1) { mergeWith() }
    }

    @Test
    fun `Polling until the checks pass and the pull request is mergeable`() {
        every { client.getPullRequestStatuses("ws", "repo", 12) } returnsMany listOf(
            listOf("INPROGRESS"),
            listOf("SUCCESSFUL"),
            listOf("SUCCESSFUL"),
        )
        every { mergeWith() } returnsMany listOf(
            BitbucketCloudMergeOutcome.NOT_MERGEABLE,
            BitbucketCloudMergeOutcome.MERGED,
        )
        val pr = createPR()
        assertEquals(SCMPullRequestStatus.MERGED, pr.status)
        verify(exactly = 3) { client.getPullRequestStatuses("ws", "repo", 12) }
        verify(exactly = 2) { mergeWith() }
    }

    @Test
    fun `Asynchronous merge completes when the pull request is merged`() {
        every { client.getPullRequestStatuses("ws", "repo", 12) } returns emptyList()
        every { mergeWith() } returns BitbucketCloudMergeOutcome.PENDING
        every { client.getPullRequest("ws", "repo", 12) } returnsMany listOf(pr("OPEN"), pr("MERGED"))
        val pr = createPR()
        assertEquals(SCMPullRequestStatus.MERGED, pr.status)
        verify(exactly = 1) { mergeWith() }
    }

    @Test
    fun `Timeout when the checks never pass`() {
        every { client.getPullRequestStatuses("ws", "repo", 12) } returns listOf("INPROGRESS")
        val pr = createPR()
        assertEquals(SCMPullRequestStatus.OPEN, pr.status)
        verify { approverClient.approvePullRequest("ws", "repo", 12) }
        verify(exactly = 0) { client.mergePullRequest(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `Declined pull request stops the polling`() {
        every { client.getPullRequest("ws", "repo", 12) } returns pr("DECLINED")
        val pr = createPR()
        assertEquals(SCMPullRequestStatus.DECLINED, pr.status)
        verify(exactly = 0) { client.getPullRequestStatuses(any(), any(), any()) }
    }

    @Test
    fun `Merge with each strategy and the auto delete branch setting`() {
        every { client.getPullRequestStatuses("ws", "repo", 12) } returns listOf("SUCCESSFUL")
        BitbucketCloudMergeStrategy.entries.forEach { strategy ->
            settings = BitbucketCloudSettings(
                mergeStrategy = strategy,
                autoDeleteBranch = false,
                autoMergeTimeout = 500,
                autoMergeInterval = 10,
            )
            every { mergeWith(strategy.name, false) } returns BitbucketCloudMergeOutcome.MERGED
            assertEquals(SCMPullRequestStatus.MERGED, createPR().status)
            verify(exactly = 1) { mergeWith(strategy.name, false) }
        }
        assertEquals(listOf("merge_commit", "squash", "fast_forward"), BitbucketCloudMergeStrategy.entries.map { it.name })
    }

    @Test
    fun `Default merge strategy is squash`() {
        assertEquals(BitbucketCloudMergeStrategy.squash, BitbucketCloudSettings().mergeStrategy)
    }

    @Test
    fun `Remote auto merge is rejected before creating the pull request`() {
        assertThrows<BitbucketCloudSCMRemoteAutoMergeNotSupportedException> {
            createPR(remoteAutoMerge = true)
        }
        verify(exactly = 0) { client.createPullRequest(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `Auto approval without an auto merge identity is rejected before creating the pull request`() {
        assertThrows<BitbucketCloudSCMMissingAutoMergeIdentityException> {
            createPR(configName = "no-approver")
        }
        verify(exactly = 0) { client.createPullRequest(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `No auto merge identity needed without auto approval`() {
        val pr = createPR(configName = "no-approver", autoApproval = false)
        assertEquals(SCMPullRequestStatus.OPEN, pr.status)
    }

    @Test
    fun `Pull request by name`() {
        every { client.getPullRequest("ws", "repo", 13) } returns null
        val pr = scm().getPullRequestByName("PR-12") ?: fail("PR found")
        assertEquals("12", pr.id)
        assertEquals("PR-12", pr.name)
        assertEquals("https://bitbucket.org/ws/repo/pull-requests/12", pr.link)
        assertEquals(SCMPullRequestStatus.OPEN, pr.status)
        assertNull(scm().getPullRequestByName("PR-13"))
        assertNull(scm().getPullRequestByName("#12"))
    }

}
