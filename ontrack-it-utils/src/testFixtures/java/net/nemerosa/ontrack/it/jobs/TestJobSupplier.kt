package net.nemerosa.ontrack.it.jobs

import net.nemerosa.ontrack.job.Job
import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.JobRegistration
import net.nemerosa.ontrack.job.JobScheduler
import net.nemerosa.ontrack.job.JobScope
import net.nemerosa.ontrack.job.Schedule
import net.nemerosa.ontrack.job.orchestrator.JobOrchestratorSupplier
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Jobs of the integration tests: a job is scheduled only when supplied (#2077), so a test registers
 * its jobs here, and [refreshes][JobScheduler.refresh] the scheduler.
 */
@Component
class TestJobSupplier : JobOrchestratorSupplier {

    private val registrations = ConcurrentHashMap<JobKey, JobRegistration>()

    override val jobRegistrations: Collection<JobRegistration>
        get() = registrations.values.toList()

    /**
     * Supplies a job, and refreshes the scheduler so that it is scheduled at once.
     */
    fun schedule(jobScheduler: JobScheduler, job: Job, schedule: Schedule, scope: JobScope = JobScope.CLUSTER) {
        registrations[job.key] = JobRegistration(job, schedule, scope)
        jobScheduler.refresh()
    }

    /**
     * Stops supplying a job, and refreshes the scheduler so that it is removed at once.
     */
    fun unschedule(jobScheduler: JobScheduler, key: JobKey) {
        registrations.remove(key)
        jobScheduler.refresh()
    }
}
