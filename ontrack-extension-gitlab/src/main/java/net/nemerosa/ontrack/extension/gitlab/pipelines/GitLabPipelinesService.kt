package net.nemerosa.ontrack.extension.gitlab.pipelines

import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.model.GitLabPipelineStatuses
import java.time.Duration

/**
 * Triggering a GitLab pipeline and waiting for its completion.
 *
 * Shared by the `gitlab-pipeline` notification channel and the GitLab auto-versioning post-processing,
 * exactly as `BitbucketPipelinesService` is on the Bitbucket Cloud side. Calls to GitLab fail with their
 * REST exceptions, left to the caller to report.
 *
 * Nothing of the [GitLabConfiguration] but its identity leaves this service: the token is attached to the
 * requests by the client, as a header, and never appears in a pipeline variable, in a returned URL or in
 * an exception message.
 */
interface GitLabPipelinesService {

    /**
     * Triggers a pipeline on a reference.
     *
     * `POST /projects/:id/pipeline` answers with the pipeline object straight away, so there is no
     * correlation-id workaround here as there is for a GitHub workflow dispatch.
     *
     * @param configuration GitLab configuration, whose token needs the Developer role on the project -
     * Maintainer when [ref] is a protected branch
     * @param project Full path of the project, like `group/subgroup/project`
     * @param ref Branch or tag to run the pipeline on
     * @param variables Variables to pass to the pipeline. They are **not** masked: a secret belongs in a
     * CI/CD variable of the GitLab project, not here.
     * @return Identification of the triggered pipeline
     */
    fun trigger(
        configuration: GitLabConfiguration,
        project: String,
        ref: String,
        variables: Map<String, String>,
    ): GitLabPipelineTrigger

    /**
     * Polls a pipeline until it completes or the timeout is reached. The first poll is immediate.
     *
     * `GET /projects/:id/pipelines/:pipeline_id`
     *
     * @param configuration GitLab configuration
     * @param project Full path of the project
     * @param pipelineId Identifier of the pipeline in the instance, as [trigger] returns it
     * @param timeout Maximum time to wait
     * @param interval Time between two polls, never less than [MIN_INTERVAL]
     * @param progress Called with the status of every poll
     * @return Last status, and whether the timeout was reached before completion
     */
    fun waitForCompletion(
        configuration: GitLabConfiguration,
        project: String,
        pipelineId: Long,
        timeout: Duration,
        interval: Duration = MIN_INTERVAL,
        progress: (status: GitLabPipelineStatus) -> Unit = {},
    ): GitLabPipelineCompletion

    companion object {
        /**
         * Minimum time between two polls.
         *
         * Not for today's limits - gitlab.com allows 2,000 API requests a minute - but for the **announced**
         * tier-aware ones, which drop Free to a 100 requests-a-minute burst. A pipeline polled every second
         * would spend a sizeable part of that budget on its own.
         */
        val MIN_INTERVAL: Duration = Duration.ofSeconds(10)
    }
}

/**
 * A triggered pipeline.
 *
 * @property id Identifier of the pipeline in the instance, which the API takes
 * @property iid Number of the pipeline inside its project, which GitLab's UI displays
 * @property url Web page of the pipeline
 * @property status Status GitLab gave it on creation, typically `created` or `pending`
 */
data class GitLabPipelineTrigger(
    val id: Long,
    val iid: Long,
    val url: String,
    val status: String,
)

/**
 * Status of a pipeline at one poll.
 *
 * @property status `created`, `waiting_for_resource`, `preparing`, `pending`, `running` while it runs, then
 * `success`, `failed`, `canceled`, `skipped` or `manual`
 * @property completed Is the pipeline over?
 */
data class GitLabPipelineStatus(
    val id: Long,
    val iid: Long,
    val url: String,
    val status: String,
    val completed: Boolean,
)

/**
 * Outcome of [GitLabPipelinesService.waitForCompletion].
 *
 * @property status Last status which was polled
 * @property timedOut The pipeline was not completed when the timeout was reached
 */
data class GitLabPipelineCompletion(
    val status: GitLabPipelineStatus,
    val timedOut: Boolean,
) {
    /**
     * Completed in time and successful.
     */
    val successful: Boolean
        get() = !timedOut && status.completed && GitLabPipelineStatuses.isSuccessful(status.status)
}

/**
 * Source of time for the polling, replaced in tests.
 */
interface GitLabPipelinesTicker {
    fun currentTimeMillis(): Long
    fun sleep(millis: Long)
}

object SystemGitLabPipelinesTicker : GitLabPipelinesTicker {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun sleep(millis: Long) = Thread.sleep(millis)
}
