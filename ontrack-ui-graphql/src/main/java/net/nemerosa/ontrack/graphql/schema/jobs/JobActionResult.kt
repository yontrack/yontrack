package net.nemerosa.ontrack.graphql.schema.jobs

import net.nemerosa.ontrack.job.JobLaunch
import net.nemerosa.ontrack.job.JobLaunchStatus

/**
 * Result of an action on a job.
 *
 * @property launchStatus For a launch, whether the job was triggered, or is already running
 */
data class JobActionResult(
    val ok: Boolean,
    val error: String?,
    val launchStatus: JobLaunchStatus? = null,
) {
    companion object {
        fun ok() =
            JobActionResult(
                ok = true,
                error = null,
            )

        fun launch(id: Long, launch: JobLaunch) =
            JobActionResult(
                ok = launch.status == JobLaunchStatus.TRIGGERED,
                error = when (launch.status) {
                    JobLaunchStatus.TRIGGERED -> null
                    JobLaunchStatus.ALREADY_RUNNING -> "The job with ID = $id is already running on ${launch.runningOn}"
                    JobLaunchStatus.NOT_RUNNABLE -> "The job with ID = $id is invalid or disabled"
                },
                launchStatus = launch.status,
            )

        fun check(ok: Boolean, error: String) =
            JobActionResult(
                ok = ok,
                error = error.takeIf { !ok }
            )
    }
}