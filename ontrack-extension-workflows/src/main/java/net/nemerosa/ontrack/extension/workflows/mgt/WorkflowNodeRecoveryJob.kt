package net.nemerosa.ontrack.extension.workflows.mgt

import net.nemerosa.ontrack.extension.workflows.WorkflowConfigurationProperties
import net.nemerosa.ontrack.extension.workflows.recovery.WorkflowNodeRecoveryService
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.model.support.JobProvider
import org.springframework.stereotype.Component

/**
 * Recovers the workflow nodes left unfinished by a stopped Yontrack process.
 */
@Component
class WorkflowNodeRecoveryJob(
    private val workflowNodeRecoveryService: WorkflowNodeRecoveryService,
    private val workflowConfigurationProperties: WorkflowConfigurationProperties,
) : JobProvider {

    override fun getStartingJobs(): Collection<JobRegistration> =
        listOf(
            JobRegistration(
                job = createJob(),
                schedule = Schedule.everySeconds(workflowConfigurationProperties.recovery.interval.toSeconds()),
            )
        )

    private fun createJob() = object : Job {

        override fun getKey(): JobKey =
            WorkflowJobs.TYPE_RECOVERY.getKey("main")

        override fun getTask() = JobRun {
            workflowNodeRecoveryService.recover()
        }

        override fun getDescription(): String = "Recovery of the workflow nodes left unfinished by a stopped Yontrack process"

        override fun isDisabled(): Boolean = false

    }
}
