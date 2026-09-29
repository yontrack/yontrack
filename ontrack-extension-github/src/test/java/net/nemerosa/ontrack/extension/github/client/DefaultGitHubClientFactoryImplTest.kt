package net.nemerosa.ontrack.extension.github.client

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.git.GitConfigProperties
import net.nemerosa.ontrack.extension.git.casc.GitConfigService
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.model.deprecation.DeprecationService
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface
import org.junit.jupiter.api.Test

class DefaultGitHubClientFactoryImplTest {

    private val deprecationService = mockk<DeprecationService>(relaxed = true)

    private val factory = DefaultGitHubClientFactoryImpl(
        gitHubAppTokenService = mockk(),
        gitConfigProperties = GitConfigProperties(),
        gitConfigService = mockk<GitConfigService> {
            every { gitConnectionConfig } returns mockk(relaxed = true)
        },
        meterRegistry = SimpleMeterRegistry(),
        deprecationService = deprecationService,
    )

    @Test
    fun `Using a configuration with a password is reported as deprecated`() {
        factory.create(
            GitHubEngineConfiguration(
                name = "test",
                url = null,
                user = "user",
                password = "secret",
            )
        )
        verify(exactly = 1) {
            deprecationService.deprecatedUsage(
                DeprecationSurface.SETTINGS,
                "GitHub configuration password authentication",
                "Removed in V7. Use a token or a GitHub App instead. See #1923",
            )
        }
    }

    @Test
    fun `Using a configuration with a token is not reported`() {
        factory.create(
            GitHubEngineConfiguration(
                name = "test",
                url = null,
                oauth2Token = "token",
            )
        )
        verify(exactly = 0) { deprecationService.deprecatedUsage(any(), any(), any()) }
    }

    @Test
    fun `Using a configuration with a user and a token is not reported`() {
        factory.create(
            GitHubEngineConfiguration(
                name = "test",
                url = null,
                user = "user",
                oauth2Token = "token",
            )
        )
        verify(exactly = 0) { deprecationService.deprecatedUsage(any(), any(), any()) }
    }
}
