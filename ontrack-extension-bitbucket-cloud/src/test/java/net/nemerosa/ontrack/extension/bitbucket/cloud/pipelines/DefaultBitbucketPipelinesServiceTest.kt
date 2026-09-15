package net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.DefaultBitbucketCloudClient
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import kotlin.test.assertEquals

class DefaultBitbucketPipelinesServiceTest {

    private val configuration = BitbucketCloudConfiguration(
        name = "bbc",
        authType = BitbucketCloudAuthType.API_TOKEN,
        email = "bot@example.com",
        token = "secret",
    )

    private val client = DefaultBitbucketCloudClient(configuration)

    private val server = MockRestServiceServer.bindTo(client.template).build()

    private val clientFactory = mockk<BitbucketCloudClientFactory>().apply {
        every { getBitbucketCloudClient(configuration) } returns client
    }

    private val ticker = FakeTicker()

    private val service = DefaultBitbucketPipelinesService(clientFactory, ticker)

    private val pipelines = "https://api.bitbucket.org/2.0/repositories/ws/repo/pipelines/"

    private fun pipelineJson(state: String, result: String? = null) = """
        {
            "type": "pipeline",
            "uuid": "{p-1}",
            "build_number": 42,
            "state": {
                "name": "$state"
                ${result?.let { """, "result": {"name": "$it"}""" } ?: ""}
            }
        }
    """.trimIndent()

    @Test
    fun `Triggering a custom pipeline with variables`() {
        server.expect(requestTo(pipelines))
            .andExpect(method(HttpMethod.POST))
            .andExpect(
                content().json(
                    """
                    {
                        "target": {
                            "type": "pipeline_ref_target",
                            "ref_type": "branch",
                            "ref_name": "main",
                            "selector": {"type": "custom", "pattern": "yontrack-echo"}
                        },
                        "variables": [
                            {"key": "MESSAGE", "value": "hello", "secured": false}
                        ]
                    }
                    """.trimIndent(),
                    true
                )
            )
            .andRespond(withSuccess(pipelineJson("PENDING"), MediaType.APPLICATION_JSON))

        val trigger = service.trigger(
            configuration = configuration,
            workspace = "ws",
            repository = "repo",
            branch = "main",
            pipeline = "yontrack-echo",
            variables = mapOf("MESSAGE" to "hello"),
        )

        server.verify()
        assertEquals(
            BitbucketPipelineTrigger(
                uuid = "{p-1}",
                buildNumber = 42,
                url = "https://bitbucket.org/ws/repo/pipelines/results/42",
            ),
            trigger
        )
    }

    @Test
    fun `Triggering the default pipeline of a branch sends no selector`() {
        server.expect(requestTo(pipelines))
            .andExpect(method(HttpMethod.POST))
            .andExpect(
                content().json(
                    """
                    {
                        "target": {
                            "type": "pipeline_ref_target",
                            "ref_type": "branch",
                            "ref_name": "release/1.0"
                        },
                        "variables": []
                    }
                    """.trimIndent(),
                    true
                )
            )
            .andRespond(withSuccess(pipelineJson("PENDING"), MediaType.APPLICATION_JSON))

        service.trigger(
            configuration = configuration,
            workspace = "ws",
            repository = "repo",
            branch = "release/1.0",
            pipeline = "",
            variables = emptyMap(),
        )

        server.verify()
    }

    private fun expectPoll(state: String, result: String? = null) {
        server.expect(requestTo("${pipelines}%7Bp-1%7D"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(pipelineJson(state, result), MediaType.APPLICATION_JSON))
    }

    private fun waitForCompletion(
        timeout: Duration = Duration.ofSeconds(60),
        interval: Duration = Duration.ofSeconds(10),
        progress: (BitbucketPipelineStatus) -> Unit = {},
    ) = service.waitForCompletion(
        configuration = configuration,
        workspace = "ws",
        repository = "repo",
        uuid = "{p-1}",
        timeout = timeout,
        interval = interval,
        progress = progress,
    )

    @Test
    fun `Waiting until the pipeline succeeds, reporting every poll`() {
        expectPoll("PENDING")
        expectPoll("IN_PROGRESS")
        expectPoll("COMPLETED", "SUCCESSFUL")

        val states = mutableListOf<String>()
        val completion = waitForCompletion { states += it.state }

        server.verify()
        assertEquals(listOf("PENDING", "IN_PROGRESS", "SUCCESSFUL"), states)
        assertEquals(false, completion.timedOut)
        assertEquals(true, completion.successful)
        assertEquals(
            BitbucketPipelineStatus(
                uuid = "{p-1}",
                buildNumber = 42,
                url = "https://bitbucket.org/ws/repo/pipelines/results/42",
                state = "SUCCESSFUL",
                completed = true,
            ),
            completion.status
        )
        assertEquals(listOf(10_000L, 10_000L), ticker.sleeps)
    }

    @ParameterizedTest
    @ValueSource(strings = ["FAILED", "ERROR", "STOPPED", "EXPIRED"])
    fun `Pipeline completing without success`(result: String) {
        expectPoll("COMPLETED", result)

        val completion = waitForCompletion()

        server.verify()
        assertEquals(false, completion.timedOut)
        assertEquals(false, completion.successful)
        assertEquals(result, completion.status.state)
        assertEquals(true, completion.status.completed)
        assertEquals(emptyList(), ticker.sleeps)
    }

    @Test
    fun `Polling is never faster than every 10 seconds`() {
        expectPoll("IN_PROGRESS")
        expectPoll("COMPLETED", "SUCCESSFUL")

        waitForCompletion(interval = Duration.ofSeconds(1))

        server.verify()
        assertEquals(listOf(10_000L), ticker.sleeps)
    }

    @Test
    fun `Polling at a longer interval than the minimum`() {
        expectPoll("IN_PROGRESS")
        expectPoll("COMPLETED", "SUCCESSFUL")

        waitForCompletion(interval = Duration.ofSeconds(30))

        server.verify()
        assertEquals(listOf(30_000L), ticker.sleeps)
    }

    @Test
    fun `Timeout reached before the pipeline completes`() {
        // Polls at 0, 10, 20 and 30 seconds
        repeat(4) { expectPoll("IN_PROGRESS") }

        val completion = waitForCompletion(timeout = Duration.ofSeconds(30))

        server.verify()
        assertEquals(true, completion.timedOut)
        assertEquals(false, completion.successful)
        assertEquals("IN_PROGRESS", completion.status.state)
        assertEquals(false, completion.status.completed)
    }

}

/**
 * Time which only moves when the service sleeps.
 */
class FakeTicker : BitbucketPipelinesTicker {
    var now = 0L
    val sleeps = mutableListOf<Long>()

    override fun currentTimeMillis(): Long = now

    override fun sleep(millis: Long) {
        sleeps += millis
        now += millis
    }
}
