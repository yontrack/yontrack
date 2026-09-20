package net.nemerosa.ontrack.extension.gitlab.notifications

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelineCompletion
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelineStatus
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelineTrigger
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelinesService
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactoryImpl
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.structure.*
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class GitLabPipelineNotificationChannelTest {

    private val configuration = GitLabConfiguration(
        name = "gl",
        url = "https://gitlab.com",
        token = "very-secret-token",
    )

    private val configurationService = mockk<GitLabConfigurationService>().apply {
        every { findConfiguration("gl") } returns configuration
        every { findConfiguration("unknown") } returns null
    }

    private val service = mockk<GitLabPipelinesService>()

    /**
     * Renders `${promotionLevel}` as the promotion name, leaves anything else unchanged.
     */
    private val eventTemplatingService = mockk<EventTemplatingService>().apply {
        every { render(any(), any(), any(), any()) } answers {
            (firstArg() as String).replace("\${promotionLevel}", PROMOTION)
        }
    }

    private val channel = GitLabPipelineNotificationChannel(
        gitLabConfigurationService = configurationService,
        eventTemplatingService = eventTemplatingService,
        gitLabPipelinesService = service,
    )

    private val url = "https://gitlab.com/group/project/-/pipelines/61"

    private val trigger = GitLabPipelineTrigger(id = 61, iid = 21, url = url, status = "created")

    private fun status(status: String, completed: Boolean) = GitLabPipelineStatus(
        id = 61,
        iid = 21,
        url = url,
        status = status,
        completed = completed,
    )

    private fun config(
        callMode: GitLabPipelineNotificationChannelConfigCallMode = GitLabPipelineNotificationChannelConfigCallMode.ASYNC,
        project: String = "group/project",
        ref: String = "main",
        variables: Map<String, String> = emptyMap(),
    ) = GitLabPipelineNotificationChannelConfig(
        config = "gl",
        project = project,
        ref = ref,
        variables = variables.map { (name, value) -> GitLabPipelineNotificationChannelConfigVariable(name, value) },
        callMode = callMode,
        timeoutSeconds = 60,
    )

    private fun expectTrigger(
        project: String = "group/project",
        ref: String = "main",
        variables: Map<String, String> = emptyMap(),
    ) {
        every { service.trigger(configuration, project, ref, variables) } returns trigger
    }

    private fun expectWait(vararg statuses: GitLabPipelineStatus, timedOut: Boolean = false) {
        every {
            service.waitForCompletion(configuration, "group/project", 61, Duration.ofSeconds(60), any(), any())
        } answers {
            val progress = arg<(GitLabPipelineStatus) -> Unit>(5)
            statuses.forEach(progress)
            GitLabPipelineCompletion(status = statuses.last(), timedOut = timedOut)
        }
    }

    private fun publish(config: GitLabPipelineNotificationChannelConfig) =
        mutableListOf<GitLabPipelineNotificationChannelOutput>().let { progress ->
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
            assertEquals(61, it.id)
            assertEquals(21, it.iid)
            assertEquals(url, it.url)
            assertEquals("created", it.status)
            assertEquals("group/project", it.project)
            assertEquals("main", it.ref)
        }
        verify(exactly = 0) { service.waitForCompletion(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `Pipeline which cannot be triggered is an error`() {
        every { service.trigger(any(), any(), any(), any()) } throws RuntimeException("403 Forbidden")

        val (result, _) = publish(config())

        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals("GitLab pipeline could not be triggered: 403 Forbidden", result.message)
        assertEquals(null, result.output?.id)
    }

    @Test
    fun `Sync pipeline successful, with its progress reported`() {
        expectTrigger()
        expectWait(status("running", false), status("success", true))

        val (result, progress) = publish(config(callMode = GitLabPipelineNotificationChannelConfigCallMode.SYNC))

        assertEquals(NotificationResultType.OK, result.type)
        assertEquals("success", result.output?.status)
        assertEquals(61, result.output?.id)
        assertEquals(
            listOf(null, "created", "running", "success", "success"),
            progress.map { it.status }
        )
    }

    @Test
    fun `Sync pipeline failing is an error`() {
        expectTrigger()
        expectWait(status("failed", true))

        val (result, _) = publish(config(callMode = GitLabPipelineNotificationChannelConfigCallMode.SYNC))

        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals("GitLab pipeline $url completed with status failed.", result.message)
        assertEquals("failed", result.output?.status)
    }

    @Test
    fun `Sync pipeline not completing in time is an error`() {
        expectTrigger()
        expectWait(status("running", false), timedOut = true)

        val (result, _) = publish(config(callMode = GitLabPipelineNotificationChannelConfigCallMode.SYNC))

        assertEquals(NotificationResultType.ERROR, result.type)
        assertEquals(
            "GitLab pipeline $url did not complete in 60 seconds (last status: running).",
            result.message
        )
    }

    @Test
    fun `Project, ref and variable values are templated`() {
        expectTrigger(
            project = "group/project-$PROMOTION",
            ref = "release-$PROMOTION",
            variables = mapOf("PROMOTION" to PROMOTION),
        )

        val (result, _) = publish(
            config(
                project = "group/project-\${promotionLevel}",
                ref = "release-\${promotionLevel}",
                variables = mapOf("PROMOTION" to "\${promotionLevel}"),
            )
        )

        assertEquals(NotificationResultType.OK, result.type)
        assertNotNull(result.output) {
            assertEquals("group/project-$PROMOTION", it.project)
            assertEquals("release-$PROMOTION", it.ref)
            assertEquals(
                listOf(GitLabPipelineNotificationChannelConfigVariable("PROMOTION", PROMOTION)),
                it.variables
            )
        }
    }

    @Test
    fun `The token of the configuration never reaches the output`() {
        expectTrigger(variables = mapOf("VERSION" to "1.0.0"))
        val (result, progress) = publish(config(variables = mapOf("VERSION" to "1.0.0")))

        assertEquals(NotificationResultType.OK, result.type)
        (progress + listOfNotNull(result.output)).forEach { output ->
            assertFalse(
                output.toString().contains("very-secret-token"),
                "The output does not carry the token of the configuration"
            )
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
