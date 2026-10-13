package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.Schedule
import java.time.LocalDateTime

/**
 * Jobs of a Quartz scheduler, as stored in the `JOBS` table, one row per job (#2077).
 *
 * Every replica of Yontrack reads the same rows: the [ID][JobRecord.id] of a job is the same on
 * all of them, and stable while the job exists.
 *
 * The rows are scoped by the name of the Quartz scheduler, so that schedulers of different names
 * sharing a database - the integration tests - do not see each other's jobs.
 */
interface JobRecordRepository {

    /**
     * All the jobs of a scheduler, by ID.
     */
    fun findAll(scheduler: String): List<JobRecord>

    fun findById(scheduler: String, id: Int): JobRecord?

    fun findByKey(scheduler: String, key: JobKey): JobRecord?

    /**
     * Creates the row of a job. A job which an earlier version of Yontrack recorded as paused, in
     * the settings, starts paused.
     */
    fun create(scheduler: String, key: JobKey, description: String, schedule: Schedule): JobRecord

    /**
     * Updates the names of the category and type of a job, its description and its schedule.
     */
    fun update(scheduler: String, id: Int, key: JobKey, description: String, schedule: Schedule)

    /**
     * Records whether a job is paused on its own.
     */
    fun setPaused(scheduler: String, id: Int, paused: Boolean)

    fun delete(scheduler: String, id: Int)

    /**
     * Takes the lock of the reconciliation of the jobs of a scheduler, held until the end of the
     * current transaction, waiting for it if needed.
     */
    fun lockForReconciliation(scheduler: String)

}

/**
 * A job, as stored in the `JOBS` table.
 *
 * @property id ID of the job, the same on every replica
 * @property key Key of the job, with the names of its category and type
 * @property description Description of the job, when it was last reconciled
 * @property schedule Schedule of the job
 * @property paused Is the job paused on its own, regardless of a "pause all"?
 * @property nextFireTime Next fire of the trigger of the job, if any
 */
data class JobRecord(
    val id: Int,
    val key: JobKey,
    val description: String,
    val schedule: Schedule,
    val paused: Boolean,
    val nextFireTime: LocalDateTime?,
)
