package net.nemerosa.ontrack.extension.audittrail.storage

import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.model.support.JobProvider
import org.springframework.stereotype.Component

/**
 * Probes the [evidence storage][EvidenceStorageService] every minute, so that its status — read
 * by the global messages, the health and the status page — is never older than that.
 */
@Component
class EvidenceStorageProbeJob(
    private val evidenceStorageService: EvidenceStorageService,
) : JobProvider, Job {

    override fun getStartingJobs(): Collection<JobRegistration> = listOf(
        JobRegistration(this, Schedule.EVERY_MINUTE)
    )

    override fun getKey(): JobKey = JOB_TYPE.getKey("probe")

    override fun getDescription(): String = "Probe of the evidence storage"

    override fun isDisabled(): Boolean = false

    override fun getTask() = JobRun { listener ->
        val status = evidenceStorageService.checkStatus()
        listener.message("Evidence storage ${status.state}${status.message?.let { ": $it" } ?: ""}")
    }

    companion object {
        private val JOB_TYPE: JobType = JobCategory.of("audit-trail").withName("Audit trail")
            .getType("evidence-storage").withName("Evidence storage")
    }
}
