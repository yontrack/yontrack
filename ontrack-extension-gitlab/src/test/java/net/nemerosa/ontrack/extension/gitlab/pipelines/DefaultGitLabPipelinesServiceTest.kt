package net.nemerosa.ontrack.extension.gitlab.pipelines

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.gitlab.client.DefaultGitLabClient
import net.nemerosa.ontrack.extension.gitlab.client.GitLabClientFactory
import net.nemerosa.ontrack.extension.gitlab.client.GitLabPipelineNotFoundException
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.time.Duration
import kotlin.test.assertEquals

class DefaultGitLabPipelinesServiceTest {

    private val configuration = GitLabConfiguration(
        name = "gl",
        url = "https://gitlab.com",
        token = "secret",
    )

    private val client = DefaultGitLabClient(configuration)

    private val server: MockRestServiceServer = MockRestServiceServer.bindTo(client.template).build()

    private val clientFactory = mockk<GitLabClientFactory>().apply {
        every { create(configuration) } returns client
    }

    private val ticker = FakeGitLabTicker()

    private val service = DefaultGitLabPipelinesService(clientFactory, ticker)

    private val project = "group/sub/project"
    private val encodedProject = "group%2Fsub%2Fproject"
    private val pipelineUrl = "https://gitlab.com/group/sub/project/-/pipelines/61"

    private fun pipelineJson(status: String) = """
        {
            "id": 61,
            "iid": 21,
            "project_id": 7,
            "ref": "main",
            "sha": "abcdef",
            "status": "$status",
            "source": "api",
            "web_url": "https://evil.example.com/not/the/configured/instance"
        }
    """.trimIndent()

    @Test
    fun `Triggering a pipeline with variables`() {
        server.expect(requestTo("https://gitlab.com/api/v4/projects/$encodedProject/pipeline"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(DefaultGitLabClient.PRIVATE_TOKEN_HEADER, "secret"))
            .andExpect(
                content().json(
                    """
                        {
                            "ref": "main",
                            "variables": [{"key": "VERSION", "value": "1.0.0"}]
                        }
                    """.trimIndent(),
                    true
                )
            )
            .andRespond(withSuccess(pipelineJson("created"), MediaType.APPLICATION_JSON))

        val trigger = service.trigger(
            configuration = configuration,
            project = project,
            ref = "main",
            variables = mapOf("VERSION" to "1.0.0"),
        )

        server.verify()
        assertEquals(
            GitLabPipelineTrigger(id = 61, iid = 21, url = pipelineUrl, status = "created"),
            trigger
        )
    }

    @Test
    fun `The URL of a pipeline comes from the configuration, not from the response`() {
        server.expect(requestTo("https://gitlab.com/api/v4/projects/$encodedProject/pipeline"))
            .andRespond(withSuccess(pipelineJson("created"), MediaType.APPLICATION_JSON))

        val trigger = service.trigger(configuration, project, "main", emptyMap())

        server.verify()
        assertEquals(pipelineUrl, trigger.url)
    }

    @Test
    fun `A self-managed instance builds its own pipeline URLs`() {
        val selfManaged = configuration.copy(url = "https://gitlab.example.com/")
        val selfManagedClient = DefaultGitLabClient(selfManaged)
        val selfManagedServer = MockRestServiceServer.bindTo(selfManagedClient.template).build()
        every { clientFactory.create(selfManaged) } returns selfManagedClient
        selfManagedServer.expect(requestTo("https://gitlab.example.com/api/v4/projects/$encodedProject/pipeline"))
            .andRespond(withSuccess(pipelineJson("pending"), MediaType.APPLICATION_JSON))

        val trigger = service.trigger(selfManaged, project, "main", emptyMap())

        selfManagedServer.verify()
        assertEquals("https://gitlab.example.com/group/sub/project/-/pipelines/61", trigger.url)
    }

    private fun expectPoll(status: String) {
        server.expect(requestTo("https://gitlab.com/api/v4/projects/$encodedProject/pipelines/61"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(pipelineJson(status), MediaType.APPLICATION_JSON))
    }

    private fun waitForCompletion(
        timeout: Duration = Duration.ofSeconds(60),
        interval: Duration = Duration.ofSeconds(10),
        progress: (GitLabPipelineStatus) -> Unit = {},
    ) = service.waitForCompletion(
        configuration = configuration,
        project = project,
        pipelineId = 61,
        timeout = timeout,
        interval = interval,
        progress = progress,
    )

    @Test
    fun `Waiting until the pipeline succeeds, reporting every poll`() {
        expectPoll("created")
        expectPoll("running")
        expectPoll("success")

        val statuses = mutableListOf<String>()
        val completion = waitForCompletion { statuses += it.status }

        server.verify()
        assertEquals(listOf("created", "running", "success"), statuses)
        assertEquals(false, completion.timedOut)
        assertEquals(true, completion.successful)
        assertEquals(
            GitLabPipelineStatus(id = 61, iid = 21, url = pipelineUrl, status = "success", completed = true),
            completion.status
        )
        assertEquals(listOf(10_000L, 10_000L), ticker.sleeps)
    }

    @ParameterizedTest
    @ValueSource(strings = ["failed", "canceled", "skipped", "manual"])
    fun `Pipeline completing without success`(status: String) {
        expectPoll(status)

        val completion = waitForCompletion()

        server.verify()
        assertEquals(false, completion.timedOut)
        assertEquals(false, completion.successful)
        assertEquals(status, completion.status.status)
        assertEquals(true, completion.status.completed)
        assertEquals(emptyList(), ticker.sleeps)
    }

    @ParameterizedTest
    @ValueSource(strings = ["created", "waiting_for_resource", "preparing", "pending", "running"])
    fun `Pipeline statuses which are still in flight`(status: String) {
        expectPoll(status)
        expectPoll("success")

        val completion = waitForCompletion()

        server.verify()
        assertEquals(true, completion.successful)
        assertEquals(listOf(10_000L), ticker.sleeps)
    }

    @Test
    fun `An unknown status counts as completed rather than blocking until the timeout`() {
        expectPoll("some_status_gitlab_invented")

        val completion = waitForCompletion()

        server.verify()
        assertEquals(false, completion.timedOut)
        assertEquals(false, completion.successful)
        assertEquals(true, completion.status.completed)
    }

    @Test
    fun `Polling is never faster than every 10 seconds`() {
        expectPoll("running")
        expectPoll("success")

        waitForCompletion(interval = Duration.ofSeconds(1))

        server.verify()
        assertEquals(listOf(10_000L), ticker.sleeps)
    }

    @Test
    fun `Polling at a longer interval than the minimum`() {
        expectPoll("running")
        expectPoll("success")

        waitForCompletion(interval = Duration.ofSeconds(30))

        server.verify()
        assertEquals(listOf(30_000L), ticker.sleeps)
    }

    @Test
    fun `Timeout reached before the pipeline completes`() {
        // Polls at 0, 10, 20 and 30 seconds
        repeat(4) { expectPoll("running") }

        val completion = waitForCompletion(timeout = Duration.ofSeconds(30))

        server.verify()
        assertEquals(true, completion.timedOut)
        assertEquals(false, completion.successful)
        assertEquals("running", completion.status.status)
        assertEquals(false, completion.status.completed)
    }

    @Test
    fun `A pipeline which disappears while it is followed is an error`() {
        server.expect(requestTo("https://gitlab.com/api/v4/projects/$encodedProject/pipelines/61"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertThrows<GitLabPipelineNotFoundException> { waitForCompletion() }

        server.verify()
    }
}

/**
 * Time which only moves when the service sleeps.
 */
class FakeGitLabTicker : GitLabPipelinesTicker {
    var now = 0L
    val sleeps = mutableListOf<Long>()

    override fun currentTimeMillis(): Long = now

    override fun sleep(millis: Long) {
        sleeps += millis
        now += millis
    }
}
