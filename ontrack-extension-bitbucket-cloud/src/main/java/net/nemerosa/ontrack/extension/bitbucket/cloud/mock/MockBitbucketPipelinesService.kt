package net.nemerosa.ontrack.extension.bitbucket.cloud.mock

import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines.*
import java.time.Duration

/**
 * Pipelines which are only recorded, never sent to Bitbucket. Not a bean, so that it never replaces the
 * real service - only the mock channel uses it.
 */
class MockBitbucketPipelinesService(
    private val recorder: MockBitbucketPipelinesRecorder,
    private val ticker: BitbucketPipelinesTicker = SystemBitbucketPipelinesTicker,
) : BitbucketPipelinesService {

    override fun trigger(
        configuration: BitbucketCloudConfiguration,
        workspace: String,
        repository: String,
        branch: String,
        pipeline: String?,
        variables: Map<String, String>,
    ): BitbucketPipelineTrigger {
        val run = recorder.trigger(
            config = configuration.name,
            workspace = workspace,
            repository = repository,
            branch = branch,
            pipeline = pipeline?.takeIf { it.isNotBlank() },
            variables = variables,
        )
        return BitbucketPipelineTrigger(
            uuid = run.uuid,
            buildNumber = run.buildNumber,
            url = DefaultBitbucketPipelinesService.runUrl(workspace, repository, run.buildNumber),
        )
    }

    override fun waitForCompletion(
        configuration: BitbucketCloudConfiguration,
        workspace: String,
        repository: String,
        uuid: String,
        timeout: Duration,
        interval: Duration,
        progress: (status: BitbucketPipelineStatus) -> Unit,
    ): BitbucketPipelineCompletion {
        val run = recorder.findRun(uuid) ?: error("No mock pipeline with UUID $uuid")
        fun status(state: String, completed: Boolean) = BitbucketPipelineStatus(
            uuid = run.uuid,
            buildNumber = run.buildNumber,
            url = DefaultBitbucketPipelinesService.runUrl(workspace, repository, run.buildNumber),
            state = state,
            completed = completed,
        )
        val running = status("IN_PROGRESS", false)
        progress(running)
        val duration = Duration.ofSeconds(run.durationSeconds)
        return if (duration > timeout) {
            ticker.sleep(timeout.toMillis())
            BitbucketPipelineCompletion(status = running, timedOut = true)
        } else {
            ticker.sleep(duration.toMillis())
            val final = status(run.result, true)
            progress(final)
            BitbucketPipelineCompletion(status = final, timedOut = false)
        }
    }
}
