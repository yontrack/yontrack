package net.nemerosa.ontrack.extension.license

import net.nemerosa.ontrack.model.support.NameValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevLicenseServiceTest {

    private val grantedFeatureId = LicenseFixtures.sampleFeatureId
    private val otherFeatureId = "${LicenseFixtures.sampleFeatureId}-other"

    private val licensedFeatureProviders = listOf(
        object : LicensedFeatureProvider {
            override val providedFeatures: List<ProvidedLicensedFeature> = listOf(
                LicenseFixtures.sampleProvidedFeature(),
                ProvidedLicensedFeature(id = otherFeatureId, name = "Other feature"),
            )
        }
    )

    private val keyLicense = License(
        type = "Development",
        name = "Development",
        assignee = "Yontrack development pipelines",
        active = true,
        validUntil = LocalDateTime.of(2027, 10, 22, 23, 0),
        maxProjects = 0,
        features = listOf(
            LicenseFeatureData(
                id = grantedFeatureId,
                enabled = true,
                data = listOf(NameValue("max", "10")),
            ),
        ),
    )

    private fun devLicenseService(keyLicense: License?) = DevLicenseService(
        licensedFeatureProviders = licensedFeatureProviders,
        devLicenseKeyService = object : DevLicenseKeyService {
            override val keyLicense: License? = keyLicense
        },
    )

    @Test
    fun `Without a key, the development license is not limited but enables no feature`() {
        val license = devLicenseService(keyLicense = null).license
        assertEquals(0, license.maxProjects, "Development license is not limited")
        assertEquals(null, license.validUntil)
        assertTrue(license.active)
        assertFalse(license.isFeatureEnabled(grantedFeatureId))
        assertFalse(license.isFeatureEnabled(otherFeatureId))
        assertEquals(DevLicenseService.MESSAGE_NO_KEY, license.message)
    }

    @Test
    fun `With a key, the development license enables what the key grants`() {
        val license = devLicenseService(keyLicense = keyLicense).license
        assertEquals("dev", license.type)
        assertEquals("Development", license.name)
        assertEquals(keyLicense.validUntil, license.validUntil)
        assertEquals(0, license.maxProjects)
        assertTrue(license.isFeatureEnabled(grantedFeatureId))
        assertEquals(listOf(NameValue("max", "10")), license.findFeatureData(grantedFeatureId)?.data)
        assertFalse(license.isFeatureEnabled(otherFeatureId), "Not granted by the key")
        assertEquals(DevLicenseService.MESSAGE, license.message)
    }

    @Test
    fun `A granted feature can be disabled on the development license, and enabled again`() {
        val devLicenseService = devLicenseService(keyLicense = keyLicense)
        devLicenseService.setFeatureEnabled(grantedFeatureId, false)
        assertFalse(
            devLicenseService.license.isFeatureEnabled(grantedFeatureId),
            "The feature is disabled"
        )
        assertTrue(devLicenseService.license.active, "The license stays active")

        devLicenseService.setFeatureEnabled(grantedFeatureId, true)
        assertTrue(
            devLicenseService.license.isFeatureEnabled(grantedFeatureId),
            "The feature is enabled again"
        )
    }

    @Test
    fun `A feature the key does not grant cannot be enabled`() {
        val devLicenseService = devLicenseService(keyLicense = keyLicense)
        val ex = assertThrows<DevLicenseFeatureNotGrantedException> {
            devLicenseService.setFeatureEnabled(otherFeatureId, true)
        }
        assertEquals("The development key does not grant the licensed feature: $otherFeatureId", ex.message)
        assertFalse(devLicenseService.license.isFeatureEnabled(otherFeatureId))
    }

    @Test
    fun `Without a key, no feature can be enabled`() {
        val devLicenseService = devLicenseService(keyLicense = null)
        assertThrows<DevLicenseFeatureNotGrantedException> {
            devLicenseService.setFeatureEnabled(grantedFeatureId, true)
        }
    }

    @Test
    fun `A feature the key does not grant can be disabled, which changes nothing`() {
        val devLicenseService = devLicenseService(keyLicense = keyLicense)
        devLicenseService.setFeatureEnabled(otherFeatureId, false)
        assertFalse(devLicenseService.license.isFeatureEnabled(otherFeatureId))
    }

    @Test
    fun `An unknown feature cannot be enabled or disabled`() {
        assertThrows<DevLicenseFeatureNotFoundException> {
            devLicenseService(keyLicense = keyLicense).setFeatureEnabled("no-such-feature", false)
        }
    }

}
