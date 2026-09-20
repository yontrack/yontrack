package net.nemerosa.ontrack.extension.gitlab.mock

import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.model.GitLabPipelineStatuses
import net.nemerosa.ontrack.extension.gitlab.pipelines.DefaultGitLabPipelinesService
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelineCompletion
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelineStatus
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelineTrigger
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelinesService
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelinesTicker
import net.nemerosa.ontrack.extension.gitlab.pipelines.SystemGitLabPipelinesTicker
import java.time.Duration

/**
 * Pipelines which are only recorded, never sent to GitLab. Not a bean, so that it never replaces the real
 * service - only the mock channel uses it.
 */
class MockGitLabPipelinesService(
    private val recorder: MockGitLabPipelinesRecorder,
    private val ticker: GitLabPipelinesTicker = SystemGitLabPipelinesTicker,
) : GitLabPipelinesService {

    override fun trigger(
        configuration: GitLabConfiguration,
        project: String,
        ref: String,
        variables: Map<String, String>,
    ): GitLabPipelineTrigger {
        val run = recorder.trigger(
            config = configuration.name,
            project = project,
            ref = ref,
            variables = variables,
        )
        return GitLabPipelineTrigger(
            id = run.id,
            iid = run.iid,
            url = DefaultGitLabPipelinesService.pipelineUrl(configuration, project, run.id),
            status = "created",
        )
    }

    override fun waitForCompletion(
        configuration: GitLabConfiguration,
        project: String,
        pipelineId: Long,
        timeout: Duration,
        interval: Duration,
        progress: (status: GitLabPipelineStatus) -> Unit,
    ): GitLabPipelineCompletion {
        val run = recorder.findRun(pipelineId) ?: error("No mock pipeline with id $pipelineId")
        fun status(status: String) = GitLabPipelineStatus(
            id = run.id,
            iid = run.iid,
            url = DefaultGitLabPipelinesService.pipelineUrl(configuration, project, run.id),
            status = status,
            completed = GitLabPipelineStatuses.isCompleted(status),
        )

        val running = status("running")
        progress(running)
        val duration = Duration.ofSeconds(run.durationSeconds)
        return if (duration > timeout) {
            ticker.sleep(timeout.toMillis())
            GitLabPipelineCompletion(status = running, timedOut = true)
        } else {
            ticker.sleep(duration.toMillis())
            val final = status(run.status)
            progress(final)
            GitLabPipelineCompletion(status = final, timedOut = false)
        }
    }
}
