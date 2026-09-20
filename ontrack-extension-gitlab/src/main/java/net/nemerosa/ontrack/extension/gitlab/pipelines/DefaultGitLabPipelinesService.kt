package net.nemerosa.ontrack.extension.gitlab.pipelines

import net.nemerosa.ontrack.extension.gitlab.client.GitLabClientFactory
import net.nemerosa.ontrack.extension.gitlab.client.GitLabPipelineNotFoundException
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.model.GitLabPipeline
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class DefaultGitLabPipelinesService(
    private val gitLabClientFactory: GitLabClientFactory,
    private val ticker: GitLabPipelinesTicker,
) : GitLabPipelinesService {

    @Autowired
    constructor(
        gitLabClientFactory: GitLabClientFactory,
    ) : this(gitLabClientFactory, SystemGitLabPipelinesTicker)

    override fun trigger(
        configuration: GitLabConfiguration,
        project: String,
        ref: String,
        variables: Map<String, String>,
    ): GitLabPipelineTrigger {
        val pipeline = gitLabClientFactory.create(configuration).triggerPipeline(
            project = project,
            ref = ref,
            variables = variables,
        )
        return GitLabPipelineTrigger(
            id = pipeline.id,
            iid = pipeline.iid,
            url = pipelineUrl(configuration, project, pipeline.id),
            status = pipeline.status,
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
        val client = gitLabClientFactory.create(configuration)
        val sleepMillis = maxOf(interval, GitLabPipelinesService.MIN_INTERVAL).toMillis()
        val deadline = ticker.currentTimeMillis() + timeout.toMillis()
        while (true) {
            val pipeline = client.getPipeline(project, pipelineId)
                ?: throw GitLabPipelineNotFoundException(project, pipelineId)
            val status = pipeline.toStatus(configuration, project)
            progress(status)
            if (status.completed) {
                return GitLabPipelineCompletion(status = status, timedOut = false)
            } else if (ticker.currentTimeMillis() >= deadline) {
                return GitLabPipelineCompletion(status = status, timedOut = true)
            }
            ticker.sleep(sleepMillis)
        }
    }

    private fun GitLabPipeline.toStatus(configuration: GitLabConfiguration, project: String) =
        GitLabPipelineStatus(
            id = id,
            iid = iid,
            url = pipelineUrl(configuration, project, id),
            status = status,
            completed = completed,
        )

    companion object {
        /**
         * Web page of a pipeline, built from the **configured** instance URL rather than read from the
         * `web_url` GitLab returns.
         *
         * The URL ends up stored in a notification output and rendered as a link, so it is derived from
         * what an administrator configured rather than from a response body. The two only ever differ on a
         * self-managed instance whose external URL is not the one Yontrack is pointed at, which is a
         * configuration to fix rather than a link to follow.
         */
        fun pipelineUrl(configuration: GitLabConfiguration, project: String, id: Long): String =
            "${configuration.url.trimEnd('/')}/${project.trim('/')}/-/pipelines/$id"
    }
}
