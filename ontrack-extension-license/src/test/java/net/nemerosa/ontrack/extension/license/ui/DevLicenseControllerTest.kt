package net.nemerosa.ontrack.extension.license.ui

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.license.DevLicenseFeatureNotGrantedException
import net.nemerosa.ontrack.extension.license.DevLicenseKeyService
import net.nemerosa.ontrack.extension.license.DevLicenseService
import net.nemerosa.ontrack.extension.license.LicenseFeatureData
import net.nemerosa.ontrack.extension.license.LicenseFixtures
import net.nemerosa.ontrack.extension.license.LicensedFeatureProvider
import net.nemerosa.ontrack.extension.license.ProvidedLicensedFeature
import net.nemerosa.ontrack.model.security.ApplicationManagement
import net.nemerosa.ontrack.model.security.SecurityService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevLicenseControllerTest {

    private val otherFeatureId = "${LicenseFixtures.sampleFeatureId}-other"

    private val devLicenseService = DevLicenseService(
        licensedFeatureProviders = listOf(
            object : LicensedFeatureProvider {
                override val providedFeatures: List<ProvidedLicensedFeature> = listOf(
                    LicenseFixtures.sampleProvidedFeature(),
                    ProvidedLicensedFeature(id = otherFeatureId, name = "Other feature"),
                )
            }
        ),
        devLicenseKeyService = object : DevLicenseKeyService {
            override val keyLicense = LicenseFixtures.testLicense(
                features = listOf(
                    LicenseFeatureData(id = LicenseFixtures.sampleFeatureId, enabled = true, data = emptyList())
                )
            )
        },
    )

    private val securityService: SecurityService = mockk()

    private val controller = DevLicenseController(devLicenseService, securityService)

    @Test
    fun `An administrator disables and enables a feature of the development licence`() {
        every { securityService.checkGlobalFunction(ApplicationManagement::class.java) } returns Unit

        controller.setFeatureEnabled(LicenseFixtures.sampleFeatureId, DevLicenseFeatureInput(enabled = false))
        assertFalse(devLicenseService.license.isFeatureEnabled(LicenseFixtures.sampleFeatureId))

        controller.setFeatureEnabled(LicenseFixtures.sampleFeatureId, DevLicenseFeatureInput(enabled = true))
        assertTrue(devLicenseService.license.isFeatureEnabled(LicenseFixtures.sampleFeatureId))
    }

    @Test
    fun `Only an administrator can disable a feature of the development licence`() {
        every { securityService.checkGlobalFunction(ApplicationManagement::class.java) } throws
                AccessDeniedException("Not granted")

        assertThrows<AccessDeniedException> {
            controller.setFeatureEnabled(LicenseFixtures.sampleFeatureId, DevLicenseFeatureInput(enabled = false))
        }
        assertTrue(devLicenseService.license.isFeatureEnabled(LicenseFixtures.sampleFeatureId))
    }

    @Test
    fun `Enabling a feature the development key does not grant is forbidden`() {
        every { securityService.checkGlobalFunction(ApplicationManagement::class.java) } returns Unit

        val ex = assertThrows<DevLicenseFeatureNotGrantedException> {
            controller.setFeatureEnabled(otherFeatureId, DevLicenseFeatureInput(enabled = true))
        }
        val response = controller.onFeatureNotGranted(ex)
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(
            DevLicenseErrorMessage(
                status = 403,
                message = "The development key does not grant the licensed feature: $otherFeatureId",
            ),
            response.body
        )
    }
}
