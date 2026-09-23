package net.nemerosa.ontrack.extension.findings

import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.extension.api.support.TestBranchModelMatcherProvider
import net.nemerosa.ontrack.extension.findings.license.TestLicenseService
import net.nemerosa.ontrack.extension.license.DevLicenseService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

/**
 * Test configuration shared by the integration tests of the findings.
 */
@Configuration
@Profile(RunProfile.DEV)
class FindingsITConfiguration {

    /**
     * Branch model, for the projects which register themselves.
     */
    @Bean
    fun testBranchModelMatcherProvider() = TestBranchModelMatcherProvider()

    /**
     * Development licence whose features a test can disable, to run without a licensed feature.
     */
    @Bean
    @Primary
    fun testLicenseService(devLicenseService: DevLicenseService) = TestLicenseService(devLicenseService)
}
