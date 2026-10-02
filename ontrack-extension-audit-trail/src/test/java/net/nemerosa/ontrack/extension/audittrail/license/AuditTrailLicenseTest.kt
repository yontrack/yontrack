package net.nemerosa.ontrack.extension.audittrail.license

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicensedFeatureProvider.Companion.FEATURE_AUDIT_TRAIL
import net.nemerosa.ontrack.extension.license.control.LicenseControlService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuditTrailLicenseTest {

    private val licenseControlService = mockk<LicenseControlService>()
    private val auditTrailLicense = AuditTrailLicense(licenseControlService)

    @Test
    fun `One boolean licensed feature for the audit trail`() {
        assertEquals("extension.audit-trail", FEATURE_AUDIT_TRAIL)
        val feature = AuditTrailLicensedFeatureProvider().providedFeatures.single()
        assertEquals(FEATURE_AUDIT_TRAIL, feature.id)
        assertEquals("Audit trail", feature.name)
        assertFalse(feature.alwaysEnabled)
    }

    @Test
    fun `The licence is read on every call, so that a lapsed licence stops the writing at once`() {
        every { licenseControlService.isFeatureEnabled(FEATURE_AUDIT_TRAIL) } returns true andThen false
        assertTrue(auditTrailLicense.auditTrailEnabled)
        assertFalse(auditTrailLicense.auditTrailEnabled)
    }
}
