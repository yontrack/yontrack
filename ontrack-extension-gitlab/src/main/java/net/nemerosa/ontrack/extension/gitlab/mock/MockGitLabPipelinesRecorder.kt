package net.nemerosa.ontrack.extension.gitlab.mock

import net.nemerosa.ontrack.common.RunProfile
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Pipelines "triggered" through the `mock-gitlab-pipeline` channel, for the acceptance tests.
 */
@Component
@Profile(RunProfile.DEV)
class MockGitLabPipelinesRecorder {

    private val runs = ConcurrentHashMap<Long, MockGitLabPipelineRun>()
    private val ids = AtomicLong()

    fun trigger(
        config: String,
        project: String,
        ref: String,
        variables: Map<String, String>,
    ): MockGitLabPipelineRun {
        val id = ids.incrementAndGet()
        val run = MockGitLabPipelineRun(
            id = id,
            // GitLab's `iid` counts inside the project, so it is not the instance-wide id
            iid = runs.values.count { it.config == config && it.project == project }.toLong() + 1,
            config = config,
            project = project,
            ref = ref,
            variables = variables,
        )
        runs[id] = run
        return run
    }

    fun findRun(id: Long): MockGitLabPipelineRun? = runs[id]

    fun findRuns(config: String, project: String): List<MockGitLabPipelineRun> =
        runs.values
            .filter { it.config == config && it.project == project }
            .sortedBy { it.id }
}

/**
 * A recorded pipeline run.
 *
 * Its outcome is driven by two variables:
 *
 * - [VAR_STATUS] - the final status, `success` by default
 * - [VAR_DURATION_SECONDS] - how long the pipeline takes, 0 by default
 */
data class MockGitLabPipelineRun(
    val id: Long,
    val iid: Long,
    val config: String,
    val project: String,
    val ref: String,
    val variables: Map<String, String>,
) {
    val status: String get() = variables[VAR_STATUS]?.takeIf { it.isNotBlank() } ?: "success"

    val durationSeconds: Long get() = variables[VAR_DURATION_SECONDS]?.toLongOrNull() ?: 0

    companion object {
        const val VAR_STATUS = "MOCK_STATUS"
        const val VAR_DURATION_SECONDS = "MOCK_DURATION_SECONDS"
    }
}
