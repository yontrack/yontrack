package net.nemerosa.ontrack.extension.bitbucket.cloud.notifications

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines.BitbucketPipelineCompletion
import net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines.BitbucketPipelineStatus
import net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines.BitbucketPipelineTrigger
import net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines.BitbucketPipelinesService
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactoryImpl
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.structure.*
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BitbucketPipelinesNotificationChannelTest {

    private val configuration = BitbucketCloudConfiguration(
        name = "bbc",
        authType = BitbucketCloudAuthType.ACCESS_TOKEN,
        token = "token",
    )

    private val configurationService = mockk<BitbucketCloudConfigurationService>().apply {
        every { findConfiguration("bbc") } returns configuration
        every { findConfiguration("unknown") } returns null
    }

    private val service = mockk<BitbucketPipelinesService>()

    /**
     * Renders `${promotionLevel}` as the promotion name, leaves anything else unchanged.
     */
    private val eventTemplatingService = mockk<EventTemplatingService>().apply {
        every { render(any(), any(), any(), any()) } answers {
            (firstArg() as String).replace("\${promotionLevel}", PROMOTION)
        }
    }

    private val channel = BitbucketPipelinesNotificationChannel(
        bitbucketCloudConfigurationService = configurationService,
        eventTemplatingService = eventTemplatingService,
        bitbucketPipelinesService = service,
    )

    private val trigger = BitbucketPipelineTrigger(
        uuid = "{p-1}",
        buildNumber = 42,
        url = "https://bitbucket.org/ws/repo/pipelines/results/42",
    )

    private fun status(state: String, completed: Boolean) = BitbucketPipelineStatus(
        uuid = trigger.uuid,
        buildNumber = trigger.buildNumber,
        url = trigger.url,
        state = state,
        completed = completed,
    )

    private fun config(
        callMode: BitbucketPipelinesNotificationChannelConfigCallMode = BitbucketPipelinesNotificationChannelConfigCallMode.ASYNC,
        pipeline: String? = "yontrack-echo",
        variables: Map<String, String> = emptyMap(),
        workspace: String = "ws",
        branch: String = "main",
    ) = BitbucketPipelinesNotificationChannelConfig(
        config = "bbc",
        workspace = workspace,
        repository = "repo",
        branch = branch,
        pipeline = pipeline,
        variables = variables.map { (name, value) -> BitbucketPipelinesNotificationChannelConfigVariable(name, value) },
        callMode = callMode,
        timeoutSeconds = 60,
    )

    private fun expectTrigger(
        workspace: String = "ws",
        branch: String = "main",
        pipeline: String? = "yontrack-echo",
        variables: Map<String, String> = emptyMap(),
    ) {
        every {
            service.trigger(configuration, workspace, "repo", branch, pipeline, variables)
        } returns trigger
    }

    private fun expectWait(vararg statuses: BitbucketPipelineStatus, timedOut: Boolean = false) {
        every {
            service.waitForCompletion(configuration, "ws", "repo", "{p-1}", Duration.ofSeconds(60), any(), any())
        } answers {
            val progress = arg<(BitbucketPipelineStatus) -> Unit>(6)
            statuses.forEach(progress)
            BitbucketPipelineCompletion(status = statuses.last(), timedOut = timedOut)
        }
    }

    private fun publish(config: BitbucketPipelinesNotificationChannelConfig) =
        mutableListOf<BitbucketPipelinesNotificationChannelOutput>().let { progress ->
            channel.publish(
                recordId = "1",
                config = config,
                event = promotionRunEvent(),
                context = emptyMap(),
                template = null,
            ) {
                progress += it
                it
            } to progress
        }

    @Test
    fun `Async pipeline triggered without waiting`() {
        expectTrigger()

        val (result, _) = publish(config())

        assertEquals(NotificationResultType.OK, result.type)
        assertNotNull(result.output) {
            assertEquals("{p-1}", it.uuid)
            assertEquals(42, it.buildNumber)
            assertEquals("https://bitbucket.org/ws/repo/pipelines/results/42", it.url)
            assertEquals(null, it.state)
            assertEquals("ws", it.workspace)
            assertEquals("repo", it.repository)
            assertEquals("main", it.branch)
            assertEquals("yontrack-echo", it.pipeline)
        }
        verify(exactly = 0) { service.waitForCompletion(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `Blank pipeline name triggers the default pipeline`() {
        expectTrigger(pipeline = null)

        val (result, _) = publish(config(pipeline = ""))

        assertEquals(NotificationResultType.OK, result.type)
        assertEquals(null, result.output?.pipeline)
    }

    @Test
    fun `Pipeline which cannot be triggered is an error`() {
        every { service.trigger(any(), any(), any(), any(), any(), any()) } throws RuntimeException("403 Forbidden")

        val (result, _) = publish(config())

        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals("Bitbucket pipeline could not be triggered: 403 Forbidden", result.message)
        assertEquals(null, result.output?.uuid)
    }

    @Test
    fun `Sync pipeline successful, with its progress reported`() {
        expectTrigger()
        expectWait(status("IN_PROGRESS", false), status("SUCCESSFUL", true))

        val (result, progress) = publish(config(callMode = BitbucketPipelinesNotificationChannelConfigCallMode.SYNC))

        assertEquals(NotificationResultType.OK, result.type)
        assertEquals("SUCCESSFUL", result.output?.state)
        assertEquals("{p-1}", result.output?.uuid)
        assertEquals(
            listOf(null, null, "IN_PROGRESS", "SUCCESSFUL", "SUCCESSFUL"),
            progress.map { it.state }
        )
    }

    @Test
    fun `Sync pipeline failing is an error`() {
        expectTrigger()
        expectWait(status("FAILED", true))

        val (result, _) = publish(config(callMode = BitbucketPipelinesNotificationChannelConfigCallMode.SYNC))

        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals(
            "Bitbucket pipeline https://bitbucket.org/ws/repo/pipelines/results/42 completed with state FAILED.",
            result.message
        )
        assertEquals("FAILED", result.output?.state)
    }

    @Test
    fun `Sync pipeline not completing in time is an error`() {
        expectTrigger()
        expectWait(status("IN_PROGRESS", false), timedOut = true)

        val (result, _) = publish(config(callMode = BitbucketPipelinesNotificationChannelConfigCallMode.SYNC))

        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals(
            "Bitbucket pipeline https://bitbucket.org/ws/repo/pipelines/results/42 did not complete in 60 seconds (last state: IN_PROGRESS).",
            result.message
        )
    }

    @Test
    fun `Workspace, branch, pipeline and variable values are templated`() {
        expectTrigger(
            workspace = "ws-$PROMOTION",
            branch = "release-$PROMOTION",
            pipeline = "deploy-$PROMOTION",
            variables = mapOf("PROMOTION" to PROMOTION),
        )

        val (result, _) = publish(
            config(
                workspace = "ws-\${promotionLevel}",
                branch = "release-\${promotionLevel}",
                pipeline = "deploy-\${promotionLevel}",
                variables = mapOf("PROMOTION" to "\${promotionLevel}"),
            )
        )

        assertEquals(NotificationResultType.OK, result.type)
        assertNotNull(result.output) {
            assertEquals("ws-$PROMOTION", it.workspace)
            assertEquals("release-$PROMOTION", it.branch)
            assertEquals("deploy-$PROMOTION", it.pipeline)
            assertEquals(listOf(BitbucketPipelinesNotificationChannelConfigVariable("PROMOTION", PROMOTION)), it.variables)
        }
    }

    @Test
    fun `Missing configuration is an invalid configuration`() {
        val result = channel.publish(
            recordId = "1",
            config = config().copy(config = "unknown"),
            event = promotionRunEvent(),
            context = emptyMap(),
            template = null,
        ) { it }

        assertEquals(NotificationResultType.INVALID_CONFIGURATION, result.type)
    }

    private fun promotionRunEvent(): Event {
        val project = Project.of(NameDescription.nd("project", "")).withId(ID.of(1))
        val branch = Branch.of(project, NameDescription.nd("main", "")).withId(ID.of(10))
        val promotionLevel = PromotionLevel.of(branch, NameDescription.nd(PROMOTION, "")).withId(ID.of(100))
        val build = Build.of(branch, NameDescription.nd("1", ""), Signature.of("test")).withId(ID.of(1000))
        val promotionRun = PromotionRun.of(build, promotionLevel, Signature.of("test"), null).withId(ID.of(10000))
        return EventFactoryImpl().newPromotionRun(promotionRun)
    }

    companion object {
        const val PROMOTION = "GOLD"
    }
}
