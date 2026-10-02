package net.nemerosa.ontrack.extension.audittrail.license

import net.nemerosa.ontrack.extension.license.LicensedFeatureProvider
import net.nemerosa.ontrack.extension.license.ProvidedLicensedFeature
import org.springframework.stereotype.Component

/**
 * The licensed feature of the audit trail: one boolean feature, with no licence data — no quota,
 * no count.
 *
 * Every licensed feature is enabled by the development licence, and therefore in the dev profile
 * and in every test stack.
 */
@Component
class AuditTrailLicensedFeatureProvider : LicensedFeatureProvider {

    override val providedFeatures: List<ProvidedLicensedFeature> = listOf(
        ProvidedLicensedFeature(
            id = FEATURE_AUDIT_TRAIL,
            name = FEATURE_AUDIT_TRAIL_NAME,
        )
    )

    companion object {
        /**
         * ID of the licensed feature of the audit trail
         */
        const val FEATURE_AUDIT_TRAIL = "extension.audit-trail"

        /**
         * Display name of the licensed feature of the audit trail
         */
        const val FEATURE_AUDIT_TRAIL_NAME = "Audit trail"
    }
}
