package net.nemerosa.ontrack.extension.license.ui

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.license.DevLicenseService
import net.nemerosa.ontrack.extension.license.LicenseFixtures
import net.nemerosa.ontrack.extension.license.LicensedFeatureProvider
import net.nemerosa.ontrack.extension.license.ProvidedLicensedFeature
import net.nemerosa.ontrack.model.security.ApplicationManagement
import net.nemerosa.ontrack.model.security.SecurityService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevLicenseControllerTest {

    private val devLicenseService = DevLicenseService(
        licensedFeatureProviders = listOf(
            object : LicensedFeatureProvider {
                override val providedFeatures: List<ProvidedLicensedFeature> = listOf(
                    LicenseFixtures.sampleProvidedFeature()
                )
            }
        )
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
}
