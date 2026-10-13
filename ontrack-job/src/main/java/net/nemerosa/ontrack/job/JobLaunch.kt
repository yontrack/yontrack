package net.nemerosa.ontrack.job

import java.util.concurrent.CompletableFuture

/**
 * Answer to a request to run a job now (#2079).
 *
 * @property status Whether the job was triggered
 * @property future When [triggered][JobLaunchStatus.TRIGGERED], completed once the run is over
 * @property runningOn When [already running][JobLaunchStatus.ALREADY_RUNNING], the replica running
 * the job: the ID of its Quartz instance, its host name
 */
data class JobLaunch(
    val status: JobLaunchStatus,
    val future: CompletableFuture<*>? = null,
    val runningOn: String? = null,
)

/**
 * Whether a job asked to run now was triggered.
 */
enum class JobLaunchStatus {

    /**
     * The job runs once, now, on any replica.
     */
    TRIGGERED,

    /**
     * The job is already running, on this replica or another one: it is not run again.
     */
    ALREADY_RUNNING,

    /**
     * The job is invalid or disabled, and cannot run.
     */
    NOT_RUNNABLE,

}
