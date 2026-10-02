package net.nemerosa.ontrack.extension.audittrail

import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicensedFeatureProvider.Companion.FEATURE_AUDIT_TRAIL
import net.nemerosa.ontrack.extension.audittrail.license.TestLicenseService
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import org.springframework.beans.factory.annotation.Autowired

/**
 * Support for the integration tests of the audit trail.
 */
abstract class AbstractAuditTrailITSupport : AbstractQLKTITSupport() {

    @Autowired
    protected lateinit var testLicenseService: TestLicenseService

    /**
     * Runs [code] with the licence off: whatever it changes writes no entry.
     */
    protected fun <T> withoutTrail(code: () -> T): T =
        testLicenseService.withoutFeature(FEATURE_AUDIT_TRAIL, code)

    /**
     * Creates a build whose trail is empty — created while the licence was off.
     */
    protected fun <T> Branch.untrailedBuild(init: Build.() -> T): T =
        withoutTrail { build() }.init()
}
