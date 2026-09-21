package net.nemerosa.ontrack.extension.gitlab.service

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.model.support.ConfigurationRepository
import net.nemerosa.ontrack.model.support.StartupService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Moves a GitLab configuration from the `user` / `password` pair it used to be stored with onto its single
 * `token` field.
 *
 * Configurations live as encrypted JSON in the `CONFIGURATIONS` table, not in columns, so this is not a
 * Flyway migration but a [StartupService] rewriting the stored JSON - exactly as
 * `GitHubConfigurationTokenMigration` does.
 *
 * `password` already held a personal access token in practice: the client passed it straight through as the
 * PAT and the configuration page already called it "Token". So the value is **moved as it stands** - still
 * encrypted with the same key, since the repository writes the JSON without going through the service's
 * encryption - and `user` is dropped, GitLab having no password authentication to use it for.
 *
 * Idempotent: a configuration already carrying a `token` and no `user`/`password` is left alone.
 */
@Component
class GitLabConfigurationTokenMigration(
    private val configurationRepository: ConfigurationRepository,
) : StartupService {

    private val logger: Logger = LoggerFactory.getLogger(GitLabConfigurationTokenMigration::class.java)

    override fun getName(): String = "Migration of GitLab configurations to a token"

    /**
     * Before any kind of CasC migration, like the GitHub one.
     */
    override fun startupOrder(): Int = StartupService.SYSTEM_REGISTRATION - 1

    override fun start() {
        configurationRepository.migrate(
            GitLabConfiguration::class.java,
            ::migrate
        )
    }

    private fun migrate(raw: JsonNode): GitLabConfiguration? {
        // Nothing to do for a configuration already in the new shape
        if (!raw.has(LEGACY_USER) && !raw.has(LEGACY_PASSWORD)) return null
        val name = raw.getTextField(GitLabConfiguration::name.name) ?: return null
        logger.info("Migrating GitLab configuration $name to a token")
        return GitLabConfiguration(
            name = name,
            url = raw.getTextField(GitLabConfiguration::url.name) ?: "",
            // The token wins if it is already there, so that running this twice cannot undo it
            token = raw.getTextField(GitLabConfiguration::token.name)
                ?: raw.getTextField(LEGACY_PASSWORD),
            ignoreSslCertificate = raw.path(GitLabConfiguration::ignoreSslCertificate.name).asBoolean(false),
        )
    }

    companion object {
        private const val LEGACY_USER = "user"
        private const val LEGACY_PASSWORD = "password"
    }
}
