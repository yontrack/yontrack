package net.nemerosa.ontrack.service.job

import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.model.support.JobProvider
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Probe jobs, for the acceptance tests of the jobs across several replicas (#2077): a periodic one,
 * every `ontrack.config.jobs.testing.period`, and a manual one. They do nothing but being run, which
 * their history records.
 *
 * Only given when `ontrack.config.jobs.testing.enabled` is set: never in production.
 */
@Component
class TestingJobs(
    private val ontrackConfigProperties: OntrackConfigProperties,
) : JobProvider {

    override fun getStartingJobs(): Collection<JobRegistration> {
        val testing = ontrackConfigProperties.jobs.testing
        return if (testing.enabled) {
            listOf(
                JobRegistration(
                    ProbeJob(PERIODIC),
                    Schedule(0, testing.period.toMillis(), TimeUnit.MILLISECONDS),
                ),
                JobRegistration(ProbeJob(MANUAL), Schedule.NONE),
            )
        } else {
            emptyList()
        }
    }

    private class ProbeJob(private val id: String) : Job {
        override fun getKey(): JobKey = probeJobType.getKey(id)
        override fun getTask() = JobRun { listener -> listener.message("Probe job $id") }
        override fun getDescription(): String = "Probe job: $id"
        override fun isDisabled(): Boolean = false
    }

    companion object {
        val probeJobType: JobType = JobCategory.of("testing").withName("Testing").getType("probe").withName("Probe")
        const val PERIODIC = "periodic"
        const val MANUAL = "manual"
    }
}
