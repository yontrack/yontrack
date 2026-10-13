package net.nemerosa.ontrack.service.job

import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.model.support.JobProvider
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The orchestration job, every `ontrack.config.jobs.orchestration` minutes: run once per period in
 * the cluster, it [refreshes][JobScheduler.refresh] the jobs, reconciling the jobs of the cluster
 * with the providers and the suppliers.
 *
 * Every replica also refreshes its own jobs on the same period, outside of this job.
 */
@Configuration
class JobOrchestrationConfig(
    private val configProperties: OntrackConfigProperties,
    private val jobScheduler: JobScheduler,
) {

    @Bean
    fun jobOrchestratorRegistration(): JobProvider = JobProvider {
        listOf(
            JobRegistration(
                OrchestrationJob(),
                Schedule.everyMinutes(configProperties.jobs.orchestration.toLong())
            )
        )
    }

    private inner class OrchestrationJob : Job {

        override fun getKey(): JobKey = orchestrationJobType.getKey("collection")

        override fun getTask() = JobRun { listener ->
            listener.message("Reconciling the jobs")
            jobScheduler.refresh()
        }

        override fun getDescription(): String = "collection"

        override fun isDisabled(): Boolean = false
    }

    companion object {
        val orchestrationJobType: JobType = JobCategory.CORE.getType("orchestrator").withName("Orchestrator")
    }

}
