package net.nemerosa.ontrack.service.deprecation

import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.model.deprecation.DeprecatedConfigurationPropertiesProvider
import net.nemerosa.ontrack.model.deprecation.DeprecatedConfigurationProperty
import net.nemerosa.ontrack.model.deprecation.DeprecationService
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface
import org.junit.jupiter.api.Test
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource

class DeprecatedConfigurationPropertiesCheckTest {

    private val deprecationService = mockk<DeprecationService>(relaxed = true)

    private val provider = object : DeprecatedConfigurationPropertiesProvider {
        override val deprecatedConfigurationProperties = listOf(
            DeprecatedConfigurationProperty(
                name = "ontrack.config.search.index.ignore-existing",
                message = "Removed in V6. No replacement. See #1882",
            ),
            DeprecatedConfigurationProperty(
                name = "ontrack.config.search.index.immediate",
                message = "Removed in V6. No replacement. See #1882",
            ),
        )
    }

    private fun check(vararg sources: org.springframework.core.env.PropertySource<*>) {
        val environment = StandardEnvironment().apply {
            propertySources.forEach { propertySources.remove(it.name) }
            sources.forEach { propertySources.addLast(it) }
        }
        DeprecatedConfigurationPropertiesCheck(environment, listOf(provider), deprecationService).check()
    }

    @Test
    fun `Nothing reported when no deprecated property is set`() {
        check(MapPropertySource("test", mapOf("ontrack.config.search.index.batch" to "100")))
        verify(exactly = 0) { deprecationService.deprecatedUsage(any(), any(), any()) }
    }

    @Test
    fun `A property set in camel case is reported under its canonical name`() {
        check(MapPropertySource("test", mapOf("ontrack.config.search.index.ignoreExisting" to "true")))
        verify(exactly = 1) {
            deprecationService.deprecatedUsage(
                DeprecationSurface.CONFIG,
                "ontrack.config.search.index.ignore-existing",
                "Removed in V6. No replacement. See #1882",
            )
        }
        verify(exactly = 0) {
            deprecationService.deprecatedUsage(any(), "ontrack.config.search.index.immediate", any())
        }
    }

    @Test
    fun `A property set as an environment variable is reported`() {
        check(
            SystemEnvironmentPropertySource(
                "env",
                mapOf("ONTRACK_CONFIG_SEARCH_INDEX_IMMEDIATE" to "true")
            )
        )
        verify(exactly = 1) {
            deprecationService.deprecatedUsage(
                DeprecationSurface.CONFIG,
                "ontrack.config.search.index.immediate",
                "Removed in V6. No replacement. See #1882",
            )
        }
    }
}
