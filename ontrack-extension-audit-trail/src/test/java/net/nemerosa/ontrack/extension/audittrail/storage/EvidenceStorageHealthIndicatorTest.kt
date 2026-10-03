package net.nemerosa.ontrack.extension.audittrail.storage

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicense
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicensedFeatureProvider.Companion.FEATURE_AUDIT_TRAIL
import net.nemerosa.ontrack.extension.license.control.LicenseControlService
import org.junit.jupiter.api.Test
import org.springframework.boot.health.contributor.Status
import kotlin.test.assertEquals

class EvidenceStorageHealthIndicatorTest {

    private val licenseControlService = mockk<LicenseControlService>()
    private val evidenceStorageService = mockk<EvidenceStorageService>()

    private val indicator = EvidenceStorageHealthIndicator(
        auditTrailLicense = AuditTrailLicense(licenseControlService),
        evidenceStorageService = evidenceStorageService,
    )

    private fun given(licensed: Boolean, state: EvidenceStorageState) {
        every { licenseControlService.isFeatureEnabled(FEATURE_AUDIT_TRAIL) } returns licensed
        every { evidenceStorageService.status } returns EvidenceStorageStatus(
            state = state,
            message = if (state == EvidenceStorageState.OK) null else "Some reason",
            checkedAt = Time.now(),
        )
    }

    @Test
    fun `Up when the storage is OK`() {
        given(licensed = true, state = EvidenceStorageState.OK)
        val health = indicator.health()
        assertEquals(Status.UP, health.status)
        assertEquals("OK", health.details["state"])
    }

    @Test
    fun `Degraded, never down, when the storage is unreachable`() {
        given(licensed = true, state = EvidenceStorageState.UNREACHABLE)
        val health = indicator.health()
        assertEquals(Status("DEGRADED"), health.status)
        assertEquals("UNREACHABLE", health.details["state"])
        assertEquals("Some reason", health.details["message"])
    }

    @Test
    fun `Degraded, never down, when the storage is not configured`() {
        given(licensed = true, state = EvidenceStorageState.NOT_CONFIGURED)
        assertEquals(Status("DEGRADED"), indicator.health().status)
    }

    @Test
    fun `Up when the licence is off, whatever the storage`() {
        given(licensed = false, state = EvidenceStorageState.UNREACHABLE)
        val health = indicator.health()
        assertEquals(Status.UP, health.status)
        assertEquals(false, health.details["licensed"])
    }
}
