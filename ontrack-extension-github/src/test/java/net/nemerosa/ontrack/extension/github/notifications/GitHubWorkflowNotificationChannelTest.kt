package net.nemerosa.ontrack.extension.github.notifications

import io.mockk.Runs
import io.mockk.every
import io.mockk.verify
import io.mockk.just
import io.mockk.mockk
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClient
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClientFactory
import net.nemerosa.ontrack.extension.github.client.WorkflowRun
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactoryImpl
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GitHubWorkflowNotificationChannelTest {

    private lateinit var ontrackGitHubClientFactory: OntrackGitHubClientFactory
    private lateinit var ontrackGitHubClient: OntrackGitHubClient

    private lateinit var gitHubWorkflowNotificationChannel: GitHubWorkflowNotificationChannel

    private lateinit var eventTemplatingService: EventTemplatingService

    private lateinit var gitHubConfigName: String
    private lateinit var gitHubConfig: GitHubEngineConfiguration
    private lateinit var gitHubConfigurationService: GitHubConfigurationService

    @BeforeEach
    fun init() {
        gitHubConfigName = uid("gh-")
        gitHubConfig = GitHubEngineConfiguration(
            name = gitHubConfigName,
            url = URL,
            user = "someuser",
            password = "somepassword"
        )

        gitHubConfigurationService = mockk()
        every { gitHubConfigurationService.findConfiguration(gitHubConfigName) } returns gitHubConfig

        ontrackGitHubClient = mockk()

        ontrackGitHubClientFactory = mockk()
        every { ontrackGitHubClientFactory.create(gitHubConfig) } returns ontrackGitHubClient

        eventTemplatingService = mockk()

        gitHubWorkflowNotificationChannel = GitHubWorkflowNotificationChannel(
            gitHubConfigurationService = gitHubConfigurationService,
            ontrackGitHubClientFactory = ontrackGitHubClientFactory,
            eventTemplatingService = eventTemplatingService,
        )
    }

    @Test
    fun `Async job successfully launched`() {
        val config = newGitHubWorkflowNotificationConfig()
        val event = newPromotionRunEvent()

        every {
            ontrackGitHubClient.launchWorkflowRun(
                repository = "$OWNER/$REPOSITORY",
                workflow = WORKFLOW,
                branch = REFERENCE,
                inputs = emptyMap(),
                sendId = true,
                retries = 10,
                retriesDelaySeconds = 10,
            )
        } returns 100L

        val result = gitHubWorkflowNotificationChannel.publish(
            recordId = "1",
            config = config,
            event = event,
            context = emptyMap(),
            template = null,
        ) { it }

        assertEquals(NotificationResultType.OK, result.type)
    }

    @Test
    fun `Async job with parameters successfully queued`() {
        val config = newGitHubWorkflowNotificationConfig(
            inputs = mapOf(
                "PROMOTION" to PROMOTION
            )
        )
        val event = newPromotionRunEvent()

        every {
            ontrackGitHubClient.launchWorkflowRun(
                repository = "$OWNER/$REPOSITORY",
                workflow = WORKFLOW,
                branch = REFERENCE,
                inputs = mapOf(
                    "PROMOTION" to PROMOTION
                ),
                sendId = true,
                retries = 10,
                retriesDelaySeconds = 10,
            )
        } returns 100L

        val result = gitHubWorkflowNotificationChannel.publish(
            recordId = "1",
            config = config,
            event = event,
            context = emptyMap(),
            template = null,
        ) { it }

        assertEquals(NotificationResultType.OK, result.type)
    }

    @Test
    fun `Sending the ID defaults to the GitHub configuration`() {
        useGitHubConfig(workflowSendId = false)
        every { ontrackGitHubClient.launchWorkflowRun(any(), any(), any(), any(), any(), any(), any()) } returns 100L

        gitHubWorkflowNotificationChannel.publish(
            recordId = "1",
            config = newGitHubWorkflowNotificationConfig(),
            event = newPromotionRunEvent(),
            context = emptyMap(),
            template = null,
        ) { it }

        verify { ontrackGitHubClient.launchWorkflowRun(any(), any(), any(), any(), sendId = false, any(), any()) }
    }

    @Test
    fun `Sending the ID in the channel config overrides the GitHub configuration`() {
        useGitHubConfig(workflowSendId = false)
        every { ontrackGitHubClient.launchWorkflowRun(any(), any(), any(), any(), any(), any(), any()) } returns 100L

        gitHubWorkflowNotificationChannel.publish(
            recordId = "1",
            config = newGitHubWorkflowNotificationConfig(sendId = true),
            event = newPromotionRunEvent(),
            context = emptyMap(),
            template = null,
        ) { it }

        verify { ontrackGitHubClient.launchWorkflowRun(any(), any(), any(), any(), sendId = true, any(), any()) }
    }

    @Test
    fun `Not sending the ID in the channel config overrides the GitHub configuration`() {
        every { ontrackGitHubClient.launchWorkflowRun(any(), any(), any(), any(), any(), any(), any()) } returns 100L

        gitHubWorkflowNotificationChannel.publish(
            recordId = "1",
            config = newGitHubWorkflowNotificationConfig(sendId = false),
            event = newPromotionRunEvent(),
            context = emptyMap(),
            template = null,
        ) { it }

        verify { ontrackGitHubClient.launchWorkflowRun(any(), any(), any(), any(), sendId = false, any(), any()) }
    }

    @Test
    fun `Async job not successfully launched`() {
        val config = newGitHubWorkflowNotificationConfig()
        val event = newPromotionRunEvent()

        every {
            ontrackGitHubClient.launchWorkflowRun(
                repository = "$OWNER/$REPOSITORY",
                workflow = WORKFLOW,
                branch = REFERENCE,
                inputs = emptyMap(),
                sendId = true,
                retries = 10,
                retriesDelaySeconds = 10,
            )
        } throws TimeoutException("Timeout")

        val result = gitHubWorkflowNotificationChannel.publish(
            recordId = "1",
            config = config,
            event = event,
            context = emptyMap(),
            template = null,
        ) { it }

        assertEquals(NotificationResultType.ERROR, result.type)
    }

    @Test
    fun `Sync job successfully finishing`() {
        val config = newGitHubWorkflowNotificationConfig(
            callMode = GitHubWorkflowNotificationChannelConfigCallMode.SYNC
        )
        val event = newPromotionRunEvent()

        every {
            ontrackGitHubClient.launchWorkflowRun(
                repository = "$OWNER/$REPOSITORY",
                workflow = WORKFLOW,
                branch = REFERENCE,
                inputs = emptyMap(),
                sendId = true,
                retries = 10,
                retriesDelaySeconds = 10,
            )
        } returns 100L

        every {
            ontrackGitHubClient.waitUntilWorkflowRun(
                repository = "$OWNER/$REPOSITORY",
                runId = 100,
                retries = 4,
                retriesDelaySeconds = 10,
            )
        } just Runs

        val finalRun = WorkflowRun(
            id = 100,
            headBranch = REFERENCE,
            status = "completed",
            conclusion = "success",
        )

        every {
            ontrackGitHubClient.getWorkflowRun(
                repository = "$OWNER/$REPOSITORY",
                runId = 100,
            )
        } returns finalRun

        val result = gitHubWorkflowNotificationChannel.publish(
            recordId = "1",
            config = config,
            event = event,
            context = emptyMap(),
            template = null,
        ) { it }

        assertEquals(NotificationResultType.OK, result.type)
        assertNotNull(result.output) { output ->
            assertEquals("https://github.com", output.url)
            assertEquals(OWNER, output.owner)
            assertEquals(REPOSITORY, output.repository)
            assertEquals(WORKFLOW, output.workflowId)
            assertEquals(REFERENCE, output.reference)
            assertEquals(100L, output.workflowRunId)
        }
    }

    @Test
    fun `Sync job finishing with an error`() {
        val config = newGitHubWorkflowNotificationConfig(
            callMode = GitHubWorkflowNotificationChannelConfigCallMode.SYNC
        )
        val event = newPromotionRunEvent()

        every {
            ontrackGitHubClient.launchWorkflowRun(
                repository = "$OWNER/$REPOSITORY",
                workflow = WORKFLOW,
                branch = REFERENCE,
                inputs = emptyMap(),
                sendId = true,
                retries = 10,
                retriesDelaySeconds = 10,
            )
        } returns 100L

        every {
            ontrackGitHubClient.waitUntilWorkflowRun(
                repository = "$OWNER/$REPOSITORY",
                runId = 100,
                retries = 4,
                retriesDelaySeconds = 10,
            )
        } just Runs

        val finalRun = WorkflowRun(
            id = 100,
            headBranch = REFERENCE,
            status = "completed",
            conclusion = "failed",
        )

        every {
            ontrackGitHubClient.getWorkflowRun(
                repository = "$OWNER/$REPOSITORY",
                runId = 100,
            )
        } returns finalRun

        val result = gitHubWorkflowNotificationChannel.publish(
            recordId = "1",
            config = config,
            event = event,
            context = emptyMap(),
            template = null,
        ) { it }

        assertEquals(NotificationResultType.ERROR, result.type)
        assertNotNull(result.output) { output ->
            assertEquals("https://github.com", output.url)
            assertEquals(OWNER, output.owner)
            assertEquals(REPOSITORY, output.repository)
            assertEquals(WORKFLOW, output.workflowId)
            assertEquals(REFERENCE, output.reference)
            assertEquals(100L, output.workflowRunId)
        }
    }

    private fun newGitHubWorkflowNotificationConfig(
        callMode: GitHubWorkflowNotificationChannelConfigCallMode = GitHubWorkflowNotificationChannelConfigCallMode.ASYNC,
        inputs: Map<String, String> = emptyMap(),
        sendId: Boolean? = null,
    ) = GitHubWorkflowNotificationChannelConfig(
        config = gitHubConfigName,
        owner = OWNER,
        repository = REPOSITORY,
        workflowId = WORKFLOW,
        reference = REFERENCE,
        inputs = inputs.map { (name, value) ->
            GitHubWorkflowNotificationChannelConfigInput(name, value)
        },
        callMode = callMode,
        sendId = sendId,
    )

    private fun useGitHubConfig(workflowSendId: Boolean) {
        gitHubConfig = GitHubEngineConfiguration(
            name = gitHubConfigName,
            url = URL,
            user = "someuser",
            password = "somepassword",
            workflowSendId = workflowSendId,
        )
        every { gitHubConfigurationService.findConfiguration(gitHubConfigName) } returns gitHubConfig
        every { ontrackGitHubClientFactory.create(gitHubConfig) } returns ontrackGitHubClient
    }

    private fun newPromotionRunEvent(): Event {
        val project = Project.of(NameDescription.nd("project", "")).withId(ID.of(1))
        val branch = Branch.of(project, NameDescription.nd("main", "")).withId(ID.of(10))
        val promotionLevel = PromotionLevel.of(branch, NameDescription.nd(PROMOTION, "")).withId(ID.of(100))
        val build = Build.of(branch, NameDescription.nd("1", ""), Signature.of("test")).withId(ID.of(1000))
        val promotionRun = PromotionRun.of(build, promotionLevel, Signature.of("test"), null).withId(ID.of(10000))
        val event = EventFactoryImpl().newPromotionRun(promotionRun)

        every {
            eventTemplatingService.render(any(), any(), any(), any())
        } answers {
            // Unchanged path
            it.invocation.args.first() as String
        }

        return event
    }

    companion object {
        const val URL = "https://github.com"
        const val OWNER = "yontrack"
        const val REPOSITORY = "yontrack"
        const val WORKFLOW = "main.yml"
        const val REFERENCE = "main"
        const val PROMOTION = "GOLD"
    }

}