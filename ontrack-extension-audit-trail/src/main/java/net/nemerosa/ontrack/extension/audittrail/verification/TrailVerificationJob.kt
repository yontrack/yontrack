package net.nemerosa.ontrack.extension.audittrail.verification

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.audittrail.events.AuditTrailEvents
import net.nemerosa.ontrack.extension.audittrail.metrics.AuditTrailMetrics
import net.nemerosa.ontrack.extension.audittrail.repository.TrailEntryRepository
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.metrics.increment
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.support.JobProvider
import net.nemerosa.ontrack.model.support.StorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Daily verification of the trails: the trail of every build which gained entries since the
 * previous run is verified as a whole, its evidence not re-hashed. Each trail which fails its
 * verification posts [trail.verification.failed][AuditTrailEvents.TRAIL_VERIFICATION_FAILED] and
 * is counted in [AuditTrailMetrics.verificationFailures].
 *
 * A trail which gained no entry is not verified again: a row tampered with afterwards is caught
 * when the trail next gains an entry, or by an on-demand verification.
 *
 * The runs are delimited by the IDs of the entries: a run verifies the builds having entries
 * after the last ID seen by the previous run, up to the last ID when it starts. The first run
 * verifies every trail. An entry whose transaction commits after a run started, with an ID below
 * the bound of this run, is left to the next entry of its trail.
 */
@Component
class TrailVerificationJob(
    private val trailEntryRepository: TrailEntryRepository,
    private val trailVerificationService: TrailVerificationService,
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val eventPostService: EventPostService,
    private val storageService: StorageService,
    private val meterRegistry: MeterRegistry,
) : JobProvider, Job {

    private val logger = LoggerFactory.getLogger(TrailVerificationJob::class.java)

    /**
     * One run at a time, a scheduled one or one launched by hand.
     */
    private val lock = ReentrantLock()

    override fun getStartingJobs(): Collection<JobRegistration> = listOf(
        JobRegistration(this, Schedule.EVERY_DAY)
    )

    override fun getKey(): JobKey = JOB_TYPE.getKey("verification")

    override fun getDescription(): String = "Verification of the trails which gained entries"

    override fun isDisabled(): Boolean = false

    override fun getTask() = JobRun { listener ->
        val run = verifyTrails()
        listener.message("${run.verifiedTrails} trail(s) verified, ${run.failedTrails} failed")
    }

    /**
     * Verifies the trails which gained entries since the previous run.
     *
     * @return Number of verified and failed trails
     */
    fun verifyTrails(): TrailVerificationJobRun = lock.withLock {
        securityService.asAdmin(REASON) {
            val after = storageService.find(STORE, KEY_WATERMARK, TrailVerificationWatermark::class)
                ?.lastEntryId ?: 0
            val upTo = trailEntryRepository.findLastEntryId() ?: 0
            if (upTo <= after) {
                TrailVerificationJobRun(verifiedTrails = 0, failedTrails = 0)
            } else {
                var verified = 0
                var failed = 0
                trailEntryRepository.findBuildIdsWithEntriesBetween(after, upTo).forEach { buildId ->
                    // The build may have been deleted since, its trail with it
                    val build = structureService.findBuildByID(ID.of(buildId))
                    if (build != null) {
                        val verification = trailVerificationService.verify(build, includeEvidence = false)
                        verified++
                        if (!verification.chainIntact || !verification.endorsementsValid) {
                            failed++
                            logger.warn(
                                "[audit-trail] The trail of build {} fails its verification: {}",
                                buildId,
                                verification.problems,
                            )
                            meterRegistry.increment(AuditTrailMetrics.verificationFailures)
                            eventPostService.post(AuditTrailEvents.trailVerificationFailed(build, verification))
                        }
                    }
                }
                storageService.store(STORE, KEY_WATERMARK, TrailVerificationWatermark(lastEntryId = upTo))
                TrailVerificationJobRun(verifiedTrails = verified, failedTrails = failed)
            }
        }
    }

    companion object {
        /**
         * Why the job reads the trails as administrator.
         */
        const val REASON = "audit-trail-verification"

        private val JOB_TYPE: JobType = JobCategory.of("audit-trail").withName("Audit trail")
            .getType("verification").withName("Verification of the trails")

        private val STORE: String = TrailVerificationJob::class.java.name
        private const val KEY_WATERMARK = "watermark"
    }
}

/**
 * Result of a run of the [verification job][TrailVerificationJob].
 *
 * @property verifiedTrails Number of trails verified
 * @property failedTrails Number of trails failing their verification
 */
data class TrailVerificationJobRun(
    val verifiedTrails: Int,
    val failedTrails: Int,
)

/**
 * Where the previous run of the [verification job][TrailVerificationJob] stopped.
 *
 * @property lastEntryId ID of the last entry the run covered
 */
data class TrailVerificationWatermark(
    val lastEntryId: Int,
)
