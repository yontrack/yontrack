package net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines

import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.model.BitbucketCloudPipeline
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class DefaultBitbucketPipelinesService(
    private val bitbucketCloudClientFactory: BitbucketCloudClientFactory,
    private val ticker: BitbucketPipelinesTicker,
) : BitbucketPipelinesService {

    @Autowired
    constructor(
        bitbucketCloudClientFactory: BitbucketCloudClientFactory,
    ) : this(bitbucketCloudClientFactory, SystemBitbucketPipelinesTicker)

    override fun trigger(
        configuration: BitbucketCloudConfiguration,
        workspace: String,
        repository: String,
        branch: String,
        pipeline: String?,
        variables: Map<String, String>,
    ): BitbucketPipelineTrigger {
        val run = bitbucketCloudClientFactory.getBitbucketCloudClient(configuration).triggerPipeline(
            workspace = workspace,
            repository = repository,
            branch = branch,
            pipeline = pipeline?.takeIf { it.isNotBlank() },
            variables = variables,
        )
        return BitbucketPipelineTrigger(
            uuid = run.uuid,
            buildNumber = run.buildNumber,
            url = runUrl(workspace, repository, run.buildNumber),
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
        val client = bitbucketCloudClientFactory.getBitbucketCloudClient(configuration)
        val sleepMillis = maxOf(interval, BitbucketPipelinesService.MIN_INTERVAL).toMillis()
        val deadline = ticker.currentTimeMillis() + timeout.toMillis()
        while (true) {
            val status = client.getPipeline(workspace, repository, uuid).toStatus(workspace, repository)
            progress(status)
            if (status.completed) {
                return BitbucketPipelineCompletion(status = status, timedOut = false)
            } else if (ticker.currentTimeMillis() >= deadline) {
                return BitbucketPipelineCompletion(status = status, timedOut = true)
            }
            ticker.sleep(sleepMillis)
        }
    }

    private fun BitbucketCloudPipeline.toStatus(workspace: String, repository: String) =
        BitbucketPipelineStatus(
            uuid = uuid,
            buildNumber = buildNumber,
            url = runUrl(workspace, repository, buildNumber),
            state = stateName,
            completed = completed,
        )

    companion object {
        fun runUrl(workspace: String, repository: String, buildNumber: Int) =
            "https://bitbucket.org/$workspace/$repository/pipelines/results/$buildNumber"
    }
}
