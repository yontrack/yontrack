package net.nemerosa.ontrack.extension.github.service

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.github.AbstractGitHubTestSupport
import net.nemerosa.ontrack.it.deprecatedUsages
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class GitHubConfigurationPasswordDeprecationIT : AbstractGitHubTestSupport() {

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private fun passwordUsages(code: () -> Unit) =
        meterRegistry.deprecatedUsages(
            DeprecationSurface.SETTINGS,
            "GitHub configuration password authentication",
            code
        )

    @Test
    fun `Saving a configuration using a password still works and is reported as deprecated`() {
        val usages = passwordUsages {
            val configuration = gitHubConfiguration(
                username = "user",
                password = "secret",
            )
            assertEquals("user", configuration.user)
        }
        assertEquals(1.0, usages)
    }

    @Test
    fun `Saving a configuration using a token is not reported as deprecated`() {
        val usages = passwordUsages {
            gitHubConfiguration(
                token = "some-token",
            )
        }
        assertEquals(0.0, usages)
    }
}
