package net.nemerosa.ontrack.extension.bitbucket.cloud.mock

import net.nemerosa.ontrack.common.RunProfile
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pipelines "triggered" through the `mock-bitbucket-pipelines` channel, for the acceptance tests.
 */
@Component
@Profile(RunProfile.DEV)
class MockBitbucketPipelinesRecorder {

    private val runs = ConcurrentHashMap<String, MockBitbucketPipelineRun>()
    private val buildNumbers = AtomicInteger()

    fun trigger(
        config: String,
        workspace: String,
        repository: String,
        branch: String,
        pipeline: String?,
        variables: Map<String, String>,
    ): MockBitbucketPipelineRun {
        val run = MockBitbucketPipelineRun(
            uuid = "{${UUID.randomUUID()}}",
            buildNumber = buildNumbers.incrementAndGet(),
            config = config,
            workspace = workspace,
            repository = repository,
            branch = branch,
            pipeline = pipeline,
            variables = variables,
        )
        runs[run.uuid] = run
        return run
    }

    fun findRun(uuid: String): MockBitbucketPipelineRun? = runs[uuid]

    fun findRuns(config: String, workspace: String, repository: String): List<MockBitbucketPipelineRun> =
        runs.values
            .filter { it.config == config && it.workspace == workspace && it.repository == repository }
            .sortedBy { it.buildNumber }
}

/**
 * A recorded pipeline run.
 *
 * Its outcome is driven by two variables:
 *
 * - [VAR_RESULT] - the final state, `SUCCESSFUL` by default
 * - [VAR_DURATION_SECONDS] - how long the pipeline takes, 0 by default
 */
data class MockBitbucketPipelineRun(
    val uuid: String,
    val buildNumber: Int,
    val config: String,
    val workspace: String,
    val repository: String,
    val branch: String,
    val pipeline: String?,
    val variables: Map<String, String>,
) {
    val result: String get() = variables[VAR_RESULT]?.takeIf { it.isNotBlank() } ?: "SUCCESSFUL"

    val durationSeconds: Long get() = variables[VAR_DURATION_SECONDS]?.toLongOrNull() ?: 0

    companion object {
        const val VAR_RESULT = "MOCK_RESULT"
        const val VAR_DURATION_SECONDS = "MOCK_DURATION_SECONDS"
    }
}
