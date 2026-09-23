package net.nemerosa.ontrack.extension.license

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevLicenseServiceTest {

    private val devLicenseService = DevLicenseService(
        licensedFeatureProviders = listOf(
            object : LicensedFeatureProvider {
                override val providedFeatures: List<ProvidedLicensedFeature> = listOf(
                    LicenseFixtures.sampleProvidedFeature()
                )
            }
        )
    )

    @Test
    fun `Development license is not limited`() {
        val license = devLicenseService.license
        assertEquals(0, license.maxProjects, "Development license is not limited")
    }

    @Test
    fun `Development license has all features being enabled`() {
        val license = devLicenseService.license
        assertTrue(
            license.isFeatureEnabled(LicenseFixtures.sampleFeatureId),
            "Any feature is enabled in the development license"
        )
    }

    @Test
    fun `A feature can be disabled on the development license, and enabled again`() {
        devLicenseService.setFeatureEnabled(LicenseFixtures.sampleFeatureId, false)
        assertFalse(
            devLicenseService.license.isFeatureEnabled(LicenseFixtures.sampleFeatureId),
            "The feature is disabled"
        )
        assertTrue(devLicenseService.license.active, "The license stays active")

        devLicenseService.setFeatureEnabled(LicenseFixtures.sampleFeatureId, true)
        assertTrue(
            devLicenseService.license.isFeatureEnabled(LicenseFixtures.sampleFeatureId),
            "The feature is enabled again"
        )
    }

    @Test
    fun `An unknown feature cannot be enabled or disabled`() {
        assertThrows<DevLicenseFeatureNotFoundException> {
            devLicenseService.setFeatureEnabled("no-such-feature", false)
        }
    }

}
