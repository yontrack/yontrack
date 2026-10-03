package net.nemerosa.ontrack.extension.audittrail.evidence

import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageService
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageState
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.model.support.JobProvider
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Daily sweep of the evidence storage: removes the blobs which no evidence references any longer
 * — their evidences deleted while the storage could not be used, or gone with their validation
 * runs and builds — and the uploads left behind, once written more than [DELAY] ago. The delay
 * keeps the uploads in progress out of reach.
 *
 * Nothing is swept while the storage is not configured or found unreachable.
 */
@Component
class EvidenceSweepJob(
    private val evidenceStorageService: EvidenceStorageService,
    private val evidenceBlobCollector: EvidenceBlobCollector,
) : JobProvider, Job {

    override fun getStartingJobs(): Collection<JobRegistration> = listOf(
        JobRegistration(this, Schedule.EVERY_DAY)
    )

    override fun getKey(): JobKey = JOB_TYPE.getKey("sweep")

    override fun getDescription(): String = "Sweep of the evidence storage"

    override fun isDisabled(): Boolean = false

    override fun getTask() = JobRun { listener ->
        val sweep = sweep()
        listener.message(
            if (sweep == null) {
                "Evidence storage not available: nothing swept"
            } else {
                "${sweep.removedBlobs} blob(s) and ${sweep.removedUploads} upload(s) removed"
            }
        )
    }

    /**
     * Sweeps the objects written more than [DELAY] ago.
     *
     * @return What was removed, `null` when the storage cannot be used
     */
    fun sweep(): EvidenceSweep? =
        if (evidenceStorageService.status.state == EvidenceStorageState.OK) {
            evidenceBlobCollector.sweep(writtenBefore = Instant.now().minus(DELAY))
        } else {
            null
        }

    companion object {
        /**
         * Age under which an object is never swept
         */
        val DELAY: Duration = Duration.ofHours(24)

        private val JOB_TYPE: JobType = JobCategory.of("audit-trail").withName("Audit trail")
            .getType("evidence-sweep").withName("Sweep of the evidence storage")
    }
}
