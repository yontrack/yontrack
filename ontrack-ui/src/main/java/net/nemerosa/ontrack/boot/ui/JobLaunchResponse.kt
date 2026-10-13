package net.nemerosa.ontrack.boot.ui

import net.nemerosa.ontrack.job.JobLaunch
import net.nemerosa.ontrack.job.JobLaunchStatus

/**
 * Answer of `POST /rest/admin/jobs/{id}` (#2079): an `Ack`, whose `success` tells whether the job
 * was triggered, with the reason when it was not.
 *
 * @property success Was the job triggered?
 * @property status Whether the job was triggered, or is already running - `null` for an unknown job
 * @property runningOn When already running, the replica running the job
 */
data class JobLaunchResponse(
    val success: Boolean,
    val status: JobLaunchStatus?,
    val runningOn: String?,
) {
    companion object {
        fun of(launch: JobLaunch) = JobLaunchResponse(
            success = launch.status == JobLaunchStatus.TRIGGERED,
            status = launch.status,
            runningOn = launch.runningOn,
        )
    }
}
