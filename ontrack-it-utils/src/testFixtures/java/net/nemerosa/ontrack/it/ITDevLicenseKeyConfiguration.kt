package net.nemerosa.ontrack.it

import net.nemerosa.ontrack.extension.license.DevLicenseKeyService
import net.nemerosa.ontrack.extension.license.License
import net.nemerosa.ontrack.extension.license.LicenseFeatureData
import net.nemerosa.ontrack.extension.license.LicensedFeatureProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * The integration tests run the `dev` profile, whose licence grants only what its development key
 * grants (#2107). They need no key: this base grants every licensed feature, with no limit, and the
 * toggles of `DevLicenseService` apply on top of it.
 *
 * It lives in the test fixtures, so that no production jar contains a way to grant the licensed
 * features without a key. Only a context which has the licence extension on its classpath gets it:
 * the extension is a compile-only dependency of these fixtures.
 */
@Configuration
@ConditionalOnClass(name = ["net.nemerosa.ontrack.extension.license.DevLicenseKeyService"])
class ITDevLicenseKeyConfiguration {

    @Bean
    @Primary
    fun itDevLicenseKeyService(
        licensedFeatureProviders: List<LicensedFeatureProvider>,
    ): DevLicenseKeyService = object : DevLicenseKeyService {
        override val keyLicense: License = License(
            type = "Development",
            name = "Integration tests",
            assignee = "Integration tests",
            active = true,
            validUntil = null,
            maxProjects = 0,
            features = licensedFeatureProviders.flatMap { it.providedFeatures }.map {
                LicenseFeatureData(
                    id = it.id,
                    enabled = true,
                    data = emptyList(),
                )
            },
        )
    }
}
