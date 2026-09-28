package net.nemerosa.ontrack.extension.github.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.common.untilTimeout
import net.nemerosa.ontrack.json.parse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeoutException

/**
 * Dispatches a GitHub workflow and finds its run back.
 *
 * The dispatch call is retried on transient errors only: 502, 503, 504, and failures to connect.
 * Other I/O errors, like a read timeout, are not retried, because GitHub has most likely accepted
 * the dispatch already. A 503 may also come back after GitHub has accepted the dispatch, so a retry
 * may launch a duplicate run: the unique ID passed as input still makes the lookup find exactly one of them.
 *
 * The lookup of the run fails on a client error (4xx) like a missing permission, which does not
 * go away by waiting, and on a [GitHubWorkflowRunNotFoundException] when the run is not found in time.
 *
 * @param dispatchRetryDelays Delays between two dispatch attempts. Their count is the number of retries.
 * @param sleep Waiting between two dispatch attempts
 * @param idGenerator Generation of the unique ID used to find the launched run back
 */
internal class GitHubWorkflowLauncher(
    private val dispatchRetryDelays: List<Duration> = DISPATCH_RETRY_DELAYS,
    private val sleep: (Duration) -> Unit = { Thread.sleep(it.toMillis()) },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    private val logger: Logger = LoggerFactory.getLogger(GitHubWorkflowLauncher::class.java)

    fun launch(
        client: RestTemplate,
        repository: String,
        workflow: String,
        branch: String,
        inputs: Map<String, String>,
        retries: Int,
        retriesDelaySeconds: Int,
    ): WorkflowRun {
        // Generating a unique ID to find the launched workflow back
        val id = idGenerator()
        dispatch(
            client = client,
            repository = repository,
            workflow = workflow,
            body = mapOf(
                "ref" to branch,
                "inputs" to (inputs + mapOf("id" to id)),
            ),
        )
        // Looking for the launched workflow run
        // Errors are retried by the lookup, but the last one says if waiting longer made any sense
        var lastError: Exception? = null
        return try {
            runBlocking {
                untilTimeout(
                    name = "Getting workflow run for $repository/$workflow/$branch",
                    retryCount = retries,
                    retryDelay = Duration.ofSeconds(retriesDelaySeconds.toLong()),
                ) {
                    try {
                        // Gets the list of runs
                        val runs = getWorkflowRuns(client, repository, branch)
                        // Checks the artifacts for each run
                        runs.find { run ->
                            hasWorkflowId(client, repository, run.id, id)
                        }.also {
                            lastError = null
                        }
                    } catch (ex: Exception) {
                        lastError = ex
                        throw ex
                    }
                }
            }
        } catch (ex: TimeoutException) {
            val error = lastError
            if (error is HttpClientErrorException) {
                throw error
            } else {
                throw GitHubWorkflowRunNotFoundException(repository, workflow, branch, ex)
            }
        }
    }

    private fun dispatch(
        client: RestTemplate,
        repository: String,
        workflow: String,
        body: Map<String, Any>,
    ) {
        val attempts = dispatchRetryDelays.size + 1
        var attempt = 1
        while (true) {
            try {
                client.postForLocation("/repos/$repository/actions/workflows/$workflow/dispatches", body)
                return
            } catch (ex: Exception) {
                if (!isTransient(ex)) {
                    throw ex
                } else if (attempt >= attempts) {
                    throw GitHubWorkflowDispatchException(repository, workflow, attempts, ex)
                } else {
                    val delay = dispatchRetryDelays[attempt - 1]
                    logger.warn(
                        "Dispatch of $repository/$workflow failed on attempt $attempt/$attempts, retrying in $delay: ${ex.message}"
                    )
                    sleep(delay)
                    attempt++
                }
            }
        }
    }

    private fun isTransient(ex: Exception): Boolean = when (ex) {
        is HttpServerErrorException -> ex.statusCode.value() in TRANSIENT_STATUSES
        // The request never reached GitHub
        is ResourceAccessException -> ex.cause is ConnectException ||
                ex.cause is UnknownHostException ||
                ex.cause is NoRouteToHostException
        else -> false
    }

    private fun getWorkflowRuns(
        client: RestTemplate,
        repository: String,
        branch: String,
    ): List<WorkflowRun> {
        val encodedBranch = URLEncoder.encode(branch, Charsets.UTF_8)
        return client.getForObject<JsonNode>("/repos/$repository/actions/runs?event=workflow_dispatch&branch=$encodedBranch")
            .path("workflow_runs")
            .map {
                it.parse()
            }
    }

    private fun hasWorkflowId(
        client: RestTemplate,
        repository: String,
        runId: Long,
        id: String,
    ): Boolean {
        val expectedName = "inputs-$id.properties"
        val artifacts = client.getForObject<JsonNode>("/repos/$repository/actions/runs/$runId/artifacts")
            .path("artifacts")
            .map {
                it.parse<Artifact>()
            }
        return artifacts.any {
            it.name == expectedName
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class Artifact(
        val name: String,
    )

    companion object {
        /**
         * Three attempts in total
         */
        val DISPATCH_RETRY_DELAYS: List<Duration> = listOf(
            Duration.ofSeconds(2),
            Duration.ofSeconds(4),
        )

        private val TRANSIENT_STATUSES = setOf(502, 503, 504)
    }
}
