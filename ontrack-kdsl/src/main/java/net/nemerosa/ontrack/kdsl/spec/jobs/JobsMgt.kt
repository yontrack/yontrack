package net.nemerosa.ontrack.kdsl.spec.jobs

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.JobExecutionStatusQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.JobHistoryQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.JobsQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.LaunchJobMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.PauseAllJobsMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.PauseJobMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ResumeAllJobsMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ResumeJobMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.StopJobMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.fragment.JobStatusFragment
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import java.time.LocalDateTime

/**
 * Management of the background jobs: their list and status, pause and resume, one by one or all
 * at once, and "run now".
 *
 * Needs the `ApplicationManagement` global function.
 */
class JobsMgt(connector: Connector) : Connected(connector) {

    /**
     * Jobs, optionally of one category and one type.
     *
     * @param category Key of the category
     * @param type Key of the type
     */
    fun list(category: String? = null, type: String? = null): List<JobStatus> =
        graphqlConnector.query(
            JobsQuery(
                Optional.presentIfNotNull(category),
                Optional.presentIfNotNull(type),
                Optional.present(MAX_JOBS),
            )
        )?.jobs?.pageItems?.map { it.jobStatusFragment.toJobStatus() }
            ?: emptyList()

    /**
     * Status of one job, by the key of its category and type, and its ID.
     */
    fun status(category: String, type: String, id: String): JobStatus? =
        list(category, type).firstOrNull { it.key == id }

    /**
     * Status of one job, by its numeric ID.
     */
    fun status(id: Int): JobStatus? = list().firstOrNull { it.id == id }

    /**
     * Runs of one job between two moments, oldest first.
     */
    fun history(category: String, type: String, id: String, from: LocalDateTime, to: LocalDateTime): List<JobRun> =
        graphqlConnector.query(JobHistoryQuery(category, type, from, to))
            ?.jobs?.pageItems
            ?.firstOrNull { it.key.id == id }
            ?.jobHistory
            ?.map { JobRun(startedAt = it.startedAt, endedAt = it.endedAt, status = it.status.name, message = it.message) }
            ?.sortedBy { it.startedAt }
            ?: emptyList()

    /**
     * Pauses a job.
     */
    fun pause(id: Int) = check("pause job $id") {
        graphqlConnector.mutate(PauseJobMutation(id)) { null }?.pauseJob?.let { it.ok to it.error }
    }

    /**
     * Resumes a job.
     */
    fun resume(id: Int) = check("resume job $id") {
        graphqlConnector.mutate(ResumeJobMutation(id)) { null }?.resumeJob?.let { it.ok to it.error }
    }

    /**
     * Runs a job once, now, on any replica.
     */
    fun runNow(id: Int) = check("run job $id") {
        graphqlConnector.mutate(LaunchJobMutation(id)) { null }?.launchJob?.let { it.ok to it.error }
    }

    /**
     * Stops a running job.
     */
    fun stop(id: Int) = check("stop job $id") {
        graphqlConnector.mutate(StopJobMutation(id)) { null }?.stopJob?.let { it.ok to it.error }
    }

    /**
     * Pauses the execution of all the jobs.
     */
    fun pauseAll() = check("pause all jobs") {
        graphqlConnector.mutate(PauseAllJobsMutation()) { null }?.pauseAllJobs?.let { it.ok to it.error }
    }

    /**
     * Resumes the execution of all the jobs.
     */
    fun resumeAll() = check("resume all jobs") {
        graphqlConnector.mutate(ResumeAllJobsMutation()) { null }?.resumeAllJobs?.let { it.ok to it.error }
    }

    /**
     * Is the execution of all the jobs paused?
     */
    val paused: Boolean
        get() = graphqlConnector.query(JobExecutionStatusQuery())?.jobExecutionStatus?.paused ?: false

    private fun check(action: String, code: () -> Pair<Boolean, String?>?) {
        val (ok, error) = code() ?: error("No answer to $action")
        check(ok) { "Could not $action: $error" }
    }

    companion object {
        private const val MAX_JOBS = 10_000
    }
}

private fun JobStatusFragment.toJobStatus() = JobStatus(
    id = id ?: error("Job without ID: ${key.id}"),
    category = key.type.category.key,
    type = key.type.key,
    key = key.id,
    description = description,
    paused = isPaused ?: false,
    running = isRunning ?: false,
    valid = isValid ?: true,
    disabled = isDisabled ?: false,
    runCount = runCount ?: 0,
    nextRunDate = nextRunDate,
    lastRunDate = lastRunDate,
    state = state.name,
)
