package net.nemerosa.ontrack.extension.audittrail

import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.extension.audittrail.license.TestLicenseService
import net.nemerosa.ontrack.extension.license.DevLicenseService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

/**
 * Test configuration shared by the integration tests of the audit trail.
 */
@Configuration
@Profile(RunProfile.DEV)
class AuditTrailITConfiguration {

    /**
     * Development licence whose features a test can disable, to run without a licensed feature.
     */
    @Bean
    @Primary
    fun testLicenseService(devLicenseService: DevLicenseService) = TestLicenseService(devLicenseService)
}
