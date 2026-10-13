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
 * @property runCount Number of runs on the replica which answered
 * @property state Name of the state of the job (`IDLE`, `RUNNING`, `PAUSED`...)
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
