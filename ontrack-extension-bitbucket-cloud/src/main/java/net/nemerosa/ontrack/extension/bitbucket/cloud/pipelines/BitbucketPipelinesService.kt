package net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines

import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import java.time.Duration

/**
 * Triggering a Bitbucket pipeline and waiting for its completion.
 *
 * Shared by the `bitbucket-pipelines` notification channel and the Bitbucket Cloud auto-versioning
 * post-processing. Calls to Bitbucket fail with their REST exceptions, left to the caller to report.
 */
interface BitbucketPipelinesService {

    /**
     * Triggers a pipeline on a branch.
     *
     * `POST /2.0/repositories/{workspace}/{repository}/pipelines/`
     *
     * @param configuration Bitbucket Cloud configuration, its token needs the pipelines write scope
     * @param workspace Workspace slug
     * @param repository Repository slug
     * @param branch Branch to run the pipeline on
     * @param pipeline Name of a `custom:` pipeline, `null` or blank for the branch's default pipeline
     * @param variables Non-secured pipeline variables
     * @return Identification of the triggered pipeline
     */
    fun trigger(
        configuration: BitbucketCloudConfiguration,
        workspace: String,
        repository: String,
        branch: String,
        pipeline: String?,
        variables: Map<String, String>,
    ): BitbucketPipelineTrigger

    /**
     * Polls a pipeline until it completes or the timeout is reached. The first poll is immediate.
     *
     * `GET /2.0/repositories/{workspace}/{repository}/pipelines/{uuid}`
     *
     * @param uuid UUID of the pipeline, as returned by [trigger]
     * @param timeout Maximum time to wait
     * @param interval Time between two polls, never less than [MIN_INTERVAL] because of the Bitbucket rate limit
     * @param progress Called with the status of every poll
     * @return Last status, and whether the timeout was reached before completion
     */
    fun waitForCompletion(
        configuration: BitbucketCloudConfiguration,
        workspace: String,
        repository: String,
        uuid: String,
        timeout: Duration,
        interval: Duration = MIN_INTERVAL,
        progress: (status: BitbucketPipelineStatus) -> Unit = {},
    ): BitbucketPipelineCompletion

    companion object {
        /**
         * Minimum time between two polls: Bitbucket allows 1,000 requests an hour per token.
         */
        val MIN_INTERVAL: Duration = Duration.ofSeconds(10)
    }
}

/**
 * A triggered pipeline.
 *
 * @property uuid UUID of the pipeline, between braces
 * @property buildNumber Number of the pipeline in its repository
 * @property url Web page of the pipeline run
 */
data class BitbucketPipelineTrigger(
    val uuid: String,
    val buildNumber: Int,
    val url: String,
)

/**
 * Status of a pipeline at one poll.
 *
 * @property state Result once completed (`SUCCESSFUL`, `FAILED`, `ERROR`, `STOPPED`, `EXPIRED`),
 * the state before (`PENDING`, `IN_PROGRESS`)
 * @property completed Is the pipeline over?
 */
data class BitbucketPipelineStatus(
    val uuid: String,
    val buildNumber: Int,
    val url: String,
    val state: String,
    val completed: Boolean,
)

/**
 * Outcome of [BitbucketPipelinesService.waitForCompletion].
 *
 * @property status Last status which was polled
 * @property timedOut The pipeline was not completed when the timeout was reached
 */
data class BitbucketPipelineCompletion(
    val status: BitbucketPipelineStatus,
    val timedOut: Boolean,
) {
    /**
     * Completed in time and successful.
     */
    val successful: Boolean get() = !timedOut && status.completed && status.state == STATE_SUCCESSFUL

    companion object {
        const val STATE_SUCCESSFUL = "SUCCESSFUL"
    }
}

/**
 * Source of time for the polling, replaced in tests.
 */
interface BitbucketPipelinesTicker {
    fun currentTimeMillis(): Long
    fun sleep(millis: Long)
}

object SystemBitbucketPipelinesTicker : BitbucketPipelinesTicker {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun sleep(millis: Long) = Thread.sleep(millis)
}
