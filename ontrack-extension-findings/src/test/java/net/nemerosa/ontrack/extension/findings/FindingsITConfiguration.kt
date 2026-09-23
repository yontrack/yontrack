package net.nemerosa.ontrack.extension.findings

import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.extension.api.support.TestBranchModelMatcherProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
}
