package net.nemerosa.ontrack.extension.github.client

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.ResourceAccessException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class GitHubWorkflowLauncherTest {

    private lateinit var client: RestTemplate
    private lateinit var server: MockRestServiceServer
    private val sleeps = mutableListOf<Duration>()

    private val launcher = GitHubWorkflowLauncher(
        sleep = { sleeps += it },
        idGenerator = { ID },
    )

    @BeforeEach
    fun init() {
        client = RestTemplateBuilder().rootUri(ROOT).build()
        server = MockRestServiceServer.bindTo(client).build()
    }

    @Test
    fun `Dispatch is retried on 503 and the run is found`() {
        expectDispatch().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        expectDispatch().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        expectDispatch().andRespond(withStatus(HttpStatus.NO_CONTENT))
        expectRunLookup()

        val run = launch()

        assertEquals(RUN_ID, run.id)
        assertEquals(listOf(Duration.ofSeconds(2), Duration.ofSeconds(4)), sleeps)
        server.verify()
    }

    @Test
    fun `Dispatch is retried on 502, 504 and connection failures`() {
        expectDispatch().andRespond(withStatus(HttpStatus.BAD_GATEWAY))
        expectDispatch().andRespond(withException(ConnectException("Connection refused")))
        expectDispatch().andRespond(withStatus(HttpStatus.NO_CONTENT))
        expectRunLookup()

        val run = launch()

        assertEquals(RUN_ID, run.id)
        server.verify()
    }

    @Test
    fun `Dispatch failing on every attempt ends with a dispatch exception`() {
        repeat(3) {
            expectDispatch().andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT))
        }

        val ex = assertFailsWith<GitHubWorkflowDispatchException> {
            launch()
        }

        assertEquals(3, ex.attempts)
        server.verify()
    }

    @Test
    fun `Dispatch is not retried on a client error`() {
        expectDispatch().andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY))

        assertFailsWith<HttpClientErrorException> {
            launch()
        }

        assertEquals(emptyList(), sleeps)
        server.verify()
    }

    @Test
    fun `Dispatch is not retried on a 500`() {
        expectDispatch().andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val ex = assertFailsWith<Exception> {
            launch()
        }

        assertIs<org.springframework.web.client.HttpServerErrorException>(ex)
        server.verify()
    }

    @Test
    fun `Dispatch is not retried on a read timeout, GitHub having most likely accepted it`() {
        expectDispatch().andRespond(withException(SocketTimeoutException("Read timed out")))

        assertFailsWith<ResourceAccessException> {
            launch()
        }

        assertEquals(emptyList(), sleeps)
        server.verify()
    }

    @Test
    fun `Run lookup failing on a client error is not a transient failure`() {
        expectDispatch().andRespond(withStatus(HttpStatus.NO_CONTENT))
        server.expect(once(), requestTo("$ROOT/repos/$REPOSITORY/actions/runs?event=workflow_dispatch&branch=main"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.FORBIDDEN))

        assertFailsWith<HttpClientErrorException.Forbidden> {
            launch(retries = 1)
        }

        server.verify()
    }

    @Test
    fun `Run lookup timing out ends with a run not found exception`() {
        expectDispatch().andRespond(withStatus(HttpStatus.NO_CONTENT))
        server.expect(once(), requestTo("$ROOT/repos/$REPOSITORY/actions/runs?event=workflow_dispatch&branch=main"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"workflow_runs": []}""", MediaType.APPLICATION_JSON))

        val ex = assertFailsWith<GitHubWorkflowRunNotFoundException> {
            launch(retries = 1)
        }

        assertEquals(REPOSITORY, ex.repository)
        server.verify()
    }

    private fun launch(retries: Int = 3) = launcher.launch(
        client = client,
        repository = REPOSITORY,
        workflow = WORKFLOW,
        branch = "main",
        inputs = mapOf("version" to "1.0.0"),
        retries = retries,
        retriesDelaySeconds = 0,
    )

    private fun expectDispatch() =
        server.expect(once(), requestTo("$ROOT/repos/$REPOSITORY/actions/workflows/$WORKFLOW/dispatches"))
            .andExpect(method(HttpMethod.POST))

    private fun expectRunLookup() {
        server.expect(once(), requestTo("$ROOT/repos/$REPOSITORY/actions/runs?event=workflow_dispatch&branch=main"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """
                        {
                          "workflow_runs": [
                            {
                              "id": $RUN_ID,
                              "head_branch": "main",
                              "status": "queued",
                              "conclusion": null
                            }
                          ]
                        }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )
        server.expect(once(), requestTo("$ROOT/repos/$REPOSITORY/actions/runs/$RUN_ID/artifacts"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"artifacts": [{"name": "inputs-$ID.properties"}]}""",
                    MediaType.APPLICATION_JSON
                )
            )
    }

    companion object {
        private const val ROOT = "https://api.github.test"
        private const val REPOSITORY = "org/post-processing"
        private const val WORKFLOW = "post-processing.yml"
        private const val ID = "some-id"
        private const val RUN_ID = 1234L
    }

}
