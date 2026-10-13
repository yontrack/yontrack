package net.nemerosa.ontrack.job

import java.time.LocalDateTime

/**
 * Status of a job, the same on every replica (#2079).
 *
 * @property isRunning Is the job running, on any replica?
 * @property progress Progress of the current run, if any
 * @property runCount Number of runs, in the cluster
 * @property lastRunDate Start of the last run
 * @property lastRunDurationMs Duration of the last successful run
 * @property lastErrorCount Number of runs in a row which failed, 0 after a success
 * @property lastTimeoutCount Number of runs in a row stopped by their timeout
 * @property lastError Error of the last run, if it failed
 * @property runningOn Replica running the job, if running: the ID of its Quartz instance, its host
 * name - the name of the pod in Kubernetes
 * @property runningSince Start of the current run, if running
 */
open class JobStatus(
        val id: Long,
        val key: JobKey,
        val schedule: Schedule,
        val actualSchedule: Schedule,
        val description: String,
        val isRunning: Boolean,
        val isValid: Boolean,
        val isPaused: Boolean,
        val isDisabled: Boolean,
        val progress: JobRunProgress?,
        val runCount: Long,
        val lastRunDate: LocalDateTime?,
        val lastRunDurationMs: Long,
        val nextRunDate: LocalDateTime?,
        val lastErrorCount: Long,
        val lastTimeoutCount: Long,
        val lastError: String?,
        val runningOn: String? = null,
        val runningSince: LocalDateTime? = null,
) {
    val state: JobState
        get() = if (isRunning) {
            JobState.RUNNING
        } else if (!isValid) {
            JobState.INVALID
        } else if (isDisabled) {
            JobState.DISABLED
        } else if (isPaused) {
            JobState.PAUSED
        } else {
            JobState.IDLE
        }

    val isError: Boolean
        get() = lastErrorCount > 0

    val isTimeout: Boolean
        get() = lastTimeoutCount > 0

    val progressText: String
        get() = progress?.text ?: ""

    fun canRun(): Boolean {
        return (!isRunning
                && !isDisabled
                && isValid)
    }

    fun canPause(): Boolean {
        return (schedule.period > 0
                && !isPaused
                && !isDisabled
                && isValid)
    }

    fun canResume(): Boolean {
        return (isPaused
                && !isDisabled
                && isValid)
    }

    fun canBeDeleted(): Boolean {
        return !isValid
    }

    fun canBeStopped(): Boolean {
        return isRunning
    }
}
