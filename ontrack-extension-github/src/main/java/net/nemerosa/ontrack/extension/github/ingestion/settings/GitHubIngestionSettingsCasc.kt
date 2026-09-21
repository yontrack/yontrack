package net.nemerosa.ontrack.extension.github.ingestion.settings

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.casc.context.settings.AbstractSubSettingsContext
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.SettingsManagerService
import org.springframework.stereotype.Component

@Component
class GitHubIngestionSettingsCasc(
    settingsManagerService: SettingsManagerService,
    cachedSettingsService: CachedSettingsService,
) : AbstractSubSettingsContext<GitHubIngestionSettings>(
    "github-ingestion",
    GitHubIngestionSettings::class,
    settingsManagerService,
    cachedSettingsService,
) {

    override fun adjustNodeBeforeParsing(node: JsonNode): JsonNode =
        node.ifMissing(
            GitHubIngestionSettings::retentionDays to GitHubIngestionSettings.DEFAULT_RETENTION_DAYS,
            GitHubIngestionSettings::orgProjectPrefix to GitHubIngestionSettings.DEFAULT_ORG_PROJECT_PREFIX,
            GitHubIngestionSettings::indexationInterval to GitHubIngestionSettings.DEFAULT_INDEXATION_INTERVAL,
            GitHubIngestionSettings::repositoryIncludes to GitHubIngestionSettings.DEFAULT_REPOSITORY_INCLUDES,
            GitHubIngestionSettings::repositoryExcludes to GitHubIngestionSettings.DEFAULT_REPOSITORY_EXCLUDES,
            GitHubIngestionSettings::issueServiceIdentifier to GitHubIngestionSettings.DEFAULT_ISSUE_SERVICE_IDENTIFIER,
            GitHubIngestionSettings::enabled to GitHubIngestionSettings.DEFAULT_ENABLED,
        )

    override fun obfuscate(settings: GitHubIngestionSettings) = settings.obfuscate()
}