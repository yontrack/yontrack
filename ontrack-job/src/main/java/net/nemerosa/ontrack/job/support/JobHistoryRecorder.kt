package net.nemerosa.ontrack.job.support

import net.nemerosa.ontrack.job.JobKey
import java.time.LocalDateTime

interface JobHistoryRecorder {

    /**
     * Records a run of a job.
     *
     * @param error Error of a failed run, `null` for a successful one
     * @param message Last message of the run: kept for a successful run, a failed one keeps its error
     * @return ID of the history item
     */
    fun record(
        jobKey: JobKey,
        startedAt: LocalDateTime,
        endedAt: LocalDateTime,
        error: String?,
        message: String? = null,
    ): Int

}