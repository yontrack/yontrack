package net.nemerosa.ontrack.kdsl.spec.jobs

import net.nemerosa.ontrack.kdsl.spec.Ontrack
import java.time.LocalDateTime

/**
 * Status of a background job.
 *
 * @property id Numeric ID of the job, the same on every replica
 * @property category Key of the category of the job
 * @property type Key of the type of the job
 * @property key ID of the job within its type
 * @property runCount Number of runs in the cluster
 * @property state Name of the state of the job (`IDLE`, `RUNNING`, `PAUSED`...)
 * @property lastRunDurationMs Duration of the last successful run
 * @property lastError Error of the last run, if it failed
 * @property lastErrorCount Number of runs in a row which failed
 * @property lastTimeoutCount Number of runs in a row stopped by their timeout
 * @property progress Progress of the current run, empty if none
 * @property runningOn Replica running the job, if running: its host name
 * @property runningSince Start of the current run, if running
 */
data class JobStatus(
    val id: Int,
    val category: String,
    val type: String,
    val key: String,
    val description: String,
    val paused: Boolean,
    val running: Boolean,
    val valid: Boolean,
    val disabled: Boolean,
    val runCount: Int,
    val nextRunDate: LocalDateTime?,
    val lastRunDate: LocalDateTime?,
    val state: String,
    val lastRunDurationMs: Long,
    val lastError: String?,
    val lastErrorCount: Long,
    val lastTimeoutCount: Long,
    val progress: String,
    val runningOn: String?,
    val runningSince: LocalDateTime?,
)

/**
 * One run of a job, from its history.
 *
 * @property status `SUCCESS` or `ERROR`
 */
data class JobRun(
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime,
    val status: String,
    val message: String?,
)

/**
 * Management of the background jobs
 */
val Ontrack.jobs: JobsMgt get() = JobsMgt(connector)
