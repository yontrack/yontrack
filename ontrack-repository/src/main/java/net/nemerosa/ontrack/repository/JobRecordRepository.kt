package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.JobRunProgress
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
 *
 * The status of the runs of a job is the same for every replica (#2079): its progress, last run and
 * counters are written by the replica running it, and whether it runs, where and since when, is read
 * from the fires Quartz is executing.
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
     * Records the start of a run: one more run, its start, no progress yet, no stop requested.
     */
    fun startRun(scheduler: String, key: JobKey, startedAt: LocalDateTime)

    /**
     * Records the progress of the current run.
     */
    fun setProgress(scheduler: String, key: JobKey, progress: JobRunProgress)

    /**
     * Records the end of a run, and clears its progress and its stop request.
     *
     * - after a success, its duration is recorded, and the counts of errors and timeouts are reset;
     * - after a failure, the error is recorded and counted; the count of timeouts is reset, unless
     *   the run was stopped by its timeout.
     *
     * @param durationMs Duration of a successful run
     * @param error Error of a failed run, `null` after a success
     * @param timedOut Was the run stopped by its timeout?
     */
    fun endRun(scheduler: String, key: JobKey, durationMs: Long, error: String?, timedOut: Boolean)

    /**
     * Counts one more run stopped by its timeout.
     */
    fun recordTimeout(scheduler: String, key: JobKey)

    /**
     * Requests the stop of the current run of a job, on whichever replica it runs.
     */
    fun requestStop(scheduler: String, key: JobKey)

    /**
     * Keys of the jobs whose stop is requested.
     */
    fun findStopRequested(scheduler: String): List<JobKey>

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
 * @property runningOn Quartz instance - the replica - executing a fire of the job, if any
 * @property runningSince Start of the fire being executed, if any
 * @property runCount Number of runs
 * @property lastRunDate Start of the last run
 * @property lastRunDurationMs Duration of the last successful run
 * @property lastError Error of the last run, if it failed
 * @property errorCount Number of runs in a row which failed
 * @property timeoutCount Number of runs in a row stopped by their timeout
 * @property progress Progress of the current run, if any
 * @property stopRequested Is the stop of the current run requested?
 */
data class JobRecord(
    val id: Int,
    val key: JobKey,
    val description: String,
    val schedule: Schedule,
    val paused: Boolean,
    val nextFireTime: LocalDateTime?,
    val runningOn: String? = null,
    val runningSince: LocalDateTime? = null,
    val runCount: Long = 0,
    val lastRunDate: LocalDateTime? = null,
    val lastRunDurationMs: Long = 0,
    val lastError: String? = null,
    val errorCount: Long = 0,
    val timeoutCount: Long = 0,
    val progress: JobRunProgress? = null,
    val stopRequested: Boolean = false,
)
