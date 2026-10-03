package net.nemerosa.ontrack.extension.audittrail.storage

import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicense
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.health.contributor.Status
import org.springframework.stereotype.Component

/**
 * Health of the evidence storage, `evidenceStorage` in the health of the instance: `DEGRADED` when
 * the licence is on and the storage is not OK — never `DOWN`, so that a missing bucket cannot take
 * the pods out. `UP` while the licence is off: nothing is stored then.
 *
 * Reads the status the probe cached: never calls the storage itself.
 */
@Component
class EvidenceStorageHealthIndicator(
    private val auditTrailLicense: AuditTrailLicense,
    private val evidenceStorageService: EvidenceStorageService,
) : HealthIndicator {

    override fun health(): Health {
        val licensed = auditTrailLicense.auditTrailEnabled
        val status = evidenceStorageService.status
        val builder = if (!licensed || status.state == EvidenceStorageState.OK) {
            Health.up()
        } else {
            Health.status(DEGRADED)
        }
        builder
            .withDetail("licensed", licensed)
            .withDetail("state", status.state.name)
            .withDetail("checkedAt", status.checkedAt.toString())
        status.message?.let { builder.withDetail("message", it) }
        return builder.build()
    }

    companion object {
        /**
         * Health status of a feature which does not work while the instance does. Registered in the
         * order of the health statuses, between `OUT_OF_SERVICE` and `UP`, and answered with HTTP 200.
         */
        val DEGRADED = Status("DEGRADED")
    }
}
