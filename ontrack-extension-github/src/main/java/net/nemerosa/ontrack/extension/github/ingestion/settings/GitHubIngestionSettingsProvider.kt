package net.nemerosa.ontrack.extension.github.ingestion.settings

import net.nemerosa.ontrack.model.security.EncryptionService
import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.*
import org.springframework.stereotype.Component

@Component
class GitHubIngestionSettingsProvider(
    private val settingsRepository: SettingsRepository,
    private val encryptionService: EncryptionService,
) : SettingsProvider<GitHubIngestionSettings> {

    override fun getSettings(): GitHubIngestionSettings {
        val values = settingsRepository.values(GitHubIngestionSettings::class.java)
        return GitHubIngestionSettings(
            token = values.getPassword(GitHubIngestionSettings::token, "") { encryptionService.decrypt(it) },
            retentionDays = values.getInt(
                GitHubIngestionSettings::retentionDays,
                GitHubIngestionSettings.DEFAULT_RETENTION_DAYS
            ),
            orgProjectPrefix = values.getBoolean(
                GitHubIngestionSettings::orgProjectPrefix,
                GitHubIngestionSettings.DEFAULT_ORG_PROJECT_PREFIX
            ),
            indexationInterval = values.getInt(
                GitHubIngestionSettings::indexationInterval,
                GitHubIngestionSettings.DEFAULT_INDEXATION_INTERVAL,
            ),
            repositoryIncludes = values.getString(
                GitHubIngestionSettings::repositoryIncludes,
                GitHubIngestionSettings.DEFAULT_REPOSITORY_INCLUDES,
            ),
            repositoryExcludes = values.getString(
                GitHubIngestionSettings::repositoryExcludes,
                GitHubIngestionSettings.DEFAULT_REPOSITORY_EXCLUDES,
            ),
            issueServiceIdentifier = values.getString(
                GitHubIngestionSettings::issueServiceIdentifier,
                GitHubIngestionSettings.DEFAULT_ISSUE_SERVICE_IDENTIFIER,
            ),
            enabled = values.getBoolean(
                GitHubIngestionSettings::enabled,
                GitHubIngestionSettings.DEFAULT_ENABLED,
            ),
        )
    }

    override fun getSettingsClass(): Class<GitHubIngestionSettings> = GitHubIngestionSettings::class.java
}