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
import org.springframework.web.client.postForObject
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
 * The dispatch asks GitHub to return the run it created (`return_run_details`), and that run is used
 * when it comes back. A GitHub rejecting the parameter (422 naming it) gets the dispatch again without it.
 * When the run is not returned, it is searched for using a unique `id` input, which needs the workflow to
 * upload an `inputs-<id>.properties` artifact. Without the `id` input, a run which is not returned is
 * a [GitHubWorkflowRunNotReturnedException].
 *
 * The dispatch call is retried on transient errors only: 502, 503, 504, and failures to connect.
 * Other I/O errors, like a read timeout, are not retried, because GitHub has most likely accepted
 * the dispatch already. A 503 may also come back after GitHub has accepted the dispatch, so a retry
 * may launch a duplicate run: the run followed is the one of the successful call, either returned by GitHub or found
 * through its unique `id` input.
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
        sendId: Boolean,
        retries: Int,
        retriesDelaySeconds: Int,
    ): Long {
        // Generating a unique ID to find the launched workflow back, if it's not returned
        val id = if (sendId) idGenerator() else null
        val body = mapOf(
            "ref" to branch,
            "inputs" to (if (id != null) inputs + ("id" to id) else inputs),
        )
        val runId = try {
            dispatch(
                client = client,
                repository = repository,
                workflow = workflow,
                body = body + (RETURN_RUN_DETAILS to true),
            )
        } catch (ex: HttpClientErrorException.UnprocessableEntity) {
            // Nothing was dispatched: a GitHub not knowing the parameter gets the dispatch without it
            if (ex.responseBodyAsString.contains(RETURN_RUN_DETAILS)) {
                logger.info("Dispatch of $repository/$workflow does not accept $RETURN_RUN_DETAILS, dispatching again without it")
                dispatch(client = client, repository = repository, workflow = workflow, body = body)
            } else {
                throw ex
            }
        }
        if (runId != null) {
            logger.info("Dispatch of $repository/$workflow returned its run $runId")
            return runId
        } else if (id == null) {
            throw GitHubWorkflowRunNotReturnedException(repository, workflow)
        }
        logger.info("Dispatch of $repository/$workflow did not return its run, searching for it")
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
                        }?.id.also {
                            lastError = null
                            if (it == null) {
                                logger.debug("Run of $repository/$workflow not found among ${runs.size} runs")
                            }
                        }
                    } catch (ex: Exception) {
                        logger.info("Looking for the run of $repository/$workflow failed: ${ex.message ?: ex::class.java.name}")
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
    ): Long? {
        val attempts = dispatchRetryDelays.size + 1
        var attempt = 1
        while (true) {
            try {
                return client.postForObject<JsonNode?>("/repos/$repository/actions/workflows/$workflow/dispatches", body)
                    ?.path("workflow_run_id")
                    ?.takeIf { it.isIntegralNumber }
                    ?.asLong()
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

        private const val RETURN_RUN_DETAILS = "return_run_details"
    }
}
