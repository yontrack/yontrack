package net.nemerosa.ontrack.extension.audittrail.license

import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicensedFeatureProvider.Companion.FEATURE_AUDIT_TRAIL
import net.nemerosa.ontrack.extension.license.control.LicenseControlService
import org.springframework.stereotype.Component

/**
 * Checks of the licence for the audit trail.
 *
 * Entries are written only while the licence is on. A trail whose first entry is written after its
 * build was created — the build predates the feature, or the licence was off — opens with a
 * `trail.opened` entry and is partial. A lapsed licence stops the writing; what was written stays
 * readable and verifiable.
 */
@Component
class AuditTrailLicense(
    private val licenseControlService: LicenseControlService,
) {

    /**
     * Whether the licence allows the audit trail. Read on every call, never cached: a licence which
     * lapses stops the writing of entries at once.
     */
    val auditTrailEnabled: Boolean
        get() = licenseControlService.isFeatureEnabled(FEATURE_AUDIT_TRAIL)
}
