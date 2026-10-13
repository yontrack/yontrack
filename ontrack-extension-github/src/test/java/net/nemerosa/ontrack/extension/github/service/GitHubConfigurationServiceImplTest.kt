package net.nemerosa.ontrack.extension.github.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.github.app.GitHubAppTokenService
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.model.support.ConfigurationRepository
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.junit.jupiter.api.Test

class GitHubConfigurationServiceImplTest {

    @Test
    fun `Deleting a configuration drops its GitHub App token`() {
        val configuration = GitHubEngineConfiguration(
            name = "test",
            url = null,
            appId = "123456",
            appPrivateKey = "key",
        )
        val configurationRepository = mockk<ConfigurationRepository>(relaxed = true)
        every {
            configurationRepository.find(GitHubEngineConfiguration::class.java, configuration.name)
        } returns configuration
        val gitHubAppTokenService = mockk<GitHubAppTokenService>(relaxed = true)

        val service = GitHubConfigurationServiceImpl(
            configurationRepository = configurationRepository,
            securityService = mockk(relaxed = true),
            encryptionService = mockk(relaxed = true),
            eventPostService = mockk(relaxed = true),
            eventFactory = mockk(relaxed = true),
            gitHubClientFactory = mockk(),
            ontrackConfigProperties = OntrackConfigProperties(),
            gitHubAppTokenService = gitHubAppTokenService,
            deprecationService = mockk(),
        )

        service.deleteConfiguration(configuration.name)

        verify { configurationRepository.delete(GitHubEngineConfiguration::class.java, configuration.name) }
        verify { gitHubAppTokenService.invalidateAppInstallationToken(configuration.name) }
    }
}
