package net.nemerosa.ontrack.extension.github.client

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import net.nemerosa.ontrack.extension.support.client.restTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
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
        client = restTemplateBuilder().baseUri(ROOT).build()
        server = MockRestServiceServer.bindTo(client).build()
    }

    @Test
    fun `Run returned by the dispatch is used without searching for it`() {
        expectDispatch()
            .andExpect(jsonPath("$.ref").value("main"))
            .andExpect(jsonPath("$.return_run_details").value(true))
            .andExpect(jsonPath("$.inputs.version").value("1.0.0"))
            .andExpect(jsonPath("$.inputs.id").value(ID))
            .andRespond(withRunDetails())

        val runId = launch()

        assertEquals(RUN_ID, runId)
        server.verify()
    }

    @Test
    fun `GitHub rejecting the run details parameter gets a dispatch without it, then the run is searched`() {
        expectDispatch()
            .andExpect(jsonPath("$.return_run_details").value(true))
            .andRespond(
                withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"message": "Invalid request.\n\n\"return_run_details\" is not a permitted key."}""")
            )
        expectDispatch()
            .andExpect(jsonPath("$.return_run_details").doesNotExist())
            .andExpect(jsonPath("$.inputs.id").value(ID))
            .andRespond(withStatus(HttpStatus.NO_CONTENT))
        expectRunLookup()

        val runId = launch()

        assertEquals(RUN_ID, runId)
        assertEquals(emptyList(), sleeps)
        server.verify()
    }

    @Test
    fun `Without sending the ID, the run returned by the dispatch is used`() {
        expectDispatch()
            .andExpect(jsonPath("$.return_run_details").value(true))
            .andExpect(jsonPath("$.inputs.version").value("1.0.0"))
            .andExpect(jsonPath("$.inputs.id").doesNotExist())
            .andRespond(withRunDetails())

        val runId = launch(sendId = false)

        assertEquals(RUN_ID, runId)
        server.verify()
    }

    @Test
    fun `Without sending the ID, a dispatch not returning its run fails without searching for it`() {
        expectDispatch()
            .andExpect(jsonPath("$.inputs.id").doesNotExist())
            .andRespond(withStatus(HttpStatus.NO_CONTENT))

        val ex = assertFailsWith<GitHubWorkflowRunNotReturnedException> {
            launch(sendId = false)
        }

        assertEquals(REPOSITORY, ex.repository)
        assertEquals(WORKFLOW, ex.workflow)
        server.verify()
    }

    @Test
    fun `Dispatch is retried on 503 and the run is found`() {
        expectDispatch().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        expectDispatch().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        expectDispatch().andRespond(withStatus(HttpStatus.NO_CONTENT))
        expectRunLookup()

        val runId = launch()

        assertEquals(RUN_ID, runId)
        assertEquals(listOf(Duration.ofSeconds(2), Duration.ofSeconds(4)), sleeps)
        server.verify()
    }

    @Test
    fun `Dispatch retried on 503 still asks for the run and uses it`() {
        expectDispatch().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        expectDispatch()
            .andExpect(jsonPath("$.return_run_details").value(true))
            .andRespond(withRunDetails())

        val runId = launch()

        assertEquals(RUN_ID, runId)
        assertEquals(listOf(Duration.ofSeconds(2)), sleeps)
        server.verify()
    }

    @Test
    fun `Dispatch is retried on 502, 504 and connection failures`() {
        expectDispatch().andRespond(withStatus(HttpStatus.BAD_GATEWAY))
        expectDispatch().andRespond(withException(ConnectException("Connection refused")))
        expectDispatch().andRespond(withStatus(HttpStatus.NO_CONTENT))
        expectRunLookup()

        val runId = launch()

        assertEquals(RUN_ID, runId)
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

    private fun launch(retries: Int = 3, sendId: Boolean = true) = launcher.launch(
        client = client,
        repository = REPOSITORY,
        workflow = WORKFLOW,
        branch = "main",
        inputs = mapOf("version" to "1.0.0"),
        sendId = sendId,
        retries = retries,
        retriesDelaySeconds = 0,
    )

    private fun expectDispatch() =
        server.expect(once(), requestTo("$ROOT/repos/$REPOSITORY/actions/workflows/$WORKFLOW/dispatches"))
            .andExpect(method(HttpMethod.POST))

    private fun withRunDetails() = withSuccess(
        """
            {
              "workflow_run_id": $RUN_ID,
              "run_url": "$ROOT/repos/$REPOSITORY/actions/runs/$RUN_ID",
              "html_url": "https://github.test/$REPOSITORY/actions/runs/$RUN_ID"
            }
        """.trimIndent(),
        MediaType.APPLICATION_JSON
    )

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
