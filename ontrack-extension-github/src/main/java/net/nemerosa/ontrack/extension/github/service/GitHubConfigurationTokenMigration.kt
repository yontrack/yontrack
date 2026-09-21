package net.nemerosa.ontrack.extension.github.service

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.security.EncryptionService
import net.nemerosa.ontrack.model.support.ConfigurationRepository
import net.nemerosa.ontrack.model.support.StartupService
import org.springframework.stereotype.Component

/**
 * This code migrates the plain tokens to encrypted ones.
 */
@Component
class GitHubConfigurationTokenMigration(
    private val configurationRepository: ConfigurationRepository,
    private val encryptionService: EncryptionService,
) : StartupService {

    override fun getName(): String = "Encryption of GitHub OAuth2 tokens"

    /**
     * Making sure to migrate the tokens before any kind of Casc migration.
     */
    override fun startupOrder(): Int = StartupService.SYSTEM_REGISTRATION - 1

    override fun start() {
        // Gets all existing configurations in a raw form & migrate them if needed
        configurationRepository.migrate(
            GitHubEngineConfiguration::class.java,
            ::migrate
        )
    }

    private fun migrate(jsonNode: JsonNode): GitHubEngineConfiguration? {
        // Based on the presence of the `appId` field, we can determine the version of the configuration
        if (!jsonNode.has(GitHubEngineConfiguration::appId.name)) {
            // Migration needed if there is a token
            val token = jsonNode.getTextField(GitHubEngineConfiguration::oauth2Token.name)
            if (!token.isNullOrBlank()) {
                // Parsing
                val oldConfiguration = jsonNode.parse<GitHubEngineConfiguration>()
                // We have a token which needs to be encrypted
                val newConfiguration = oldConfiguration.run {
                    GitHubEngineConfiguration(
                        name = name,
                        url = url,
                        user = user,
                        password = password,
                        oauth2Token = encryptionService.encrypt(oauth2Token),
                        appId = appId,
                        appPrivateKey = appPrivateKey,
                        appInstallationAccountName = appInstallationAccountName,
                    )
                }
                // Saves back the configuration
                configurationRepository.save(newConfiguration)
            }
        }
        // No migration needed
        return null
    }
}