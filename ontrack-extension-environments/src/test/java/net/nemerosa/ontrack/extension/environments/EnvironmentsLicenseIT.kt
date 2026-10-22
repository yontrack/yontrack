package net.nemerosa.ontrack.extension.environments

import net.nemerosa.ontrack.extension.license.*
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvironmentsLicenseIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var devLicenseService: DevLicenseService

    @Test
    fun `Environments feature is enabled for the development license of the integration tests`() {
        val license = devLicenseService.license
        assertTrue(license.isFeatureEnabled(EnvironmentsLicensedFeatureProvider.FEATURE_ENVIRONMENTS))
    }

    @Test
    fun `Environments feature is enabled for the prod license`() {
        val prodLicenseService = ProductionLicenseService(
            licenseConfigurationProperties = LicenseConfigurationProperties().apply {
                key = LicenseKeysFixtures.UNLIMITED
            },
            licenseKeyPath = LicenseKeysFixtures.TEST_SIGNATURE_RESOURCE_PATH,
        )
        val license = prodLicenseService.license
        assertTrue(license.isFeatureEnabled(EnvironmentsLicensedFeatureProvider.FEATURE_ENVIRONMENTS))
        assertEquals(
            0,
            license
                .findFeatureData(EnvironmentsLicensedFeatureProvider.FEATURE_ENVIRONMENTS)
                ?.findValue(EnvironmentsLicenseData.MAX_ENVIRONMENTS)
                ?.toInt()
        )
    }

    @Test
    fun `Environments feature can be limited for the prod license`() {
        val prodLicenseService = ProductionLicenseService(
            licenseConfigurationProperties = LicenseConfigurationProperties().apply {
                key = LicenseKeysFixtures.LIMITED_ENVIRONMENTS
            },
            licenseKeyPath = LicenseKeysFixtures.TEST_SIGNATURE_RESOURCE_PATH,
        )
        val license = prodLicenseService.license
        assertTrue(license.isFeatureEnabled(EnvironmentsLicensedFeatureProvider.FEATURE_ENVIRONMENTS))
        assertEquals(
            10,
            license
                .findFeatureData(EnvironmentsLicensedFeatureProvider.FEATURE_ENVIRONMENTS)
                ?.findValue(EnvironmentsLicenseData.MAX_ENVIRONMENTS)
                ?.toInt()
        )
    }

}