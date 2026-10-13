package net.nemerosa.ontrack.extension.github.autoversioning

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.values
import net.nemerosa.ontrack.model.support.getInt
import net.nemerosa.ontrack.model.support.getString
import org.springframework.stereotype.Component

@Component
class GitHubPostProcessingSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<GitHubPostProcessingSettings> {

    override fun getSettings(): GitHubPostProcessingSettings {
        val values = settingsRepository.values(GitHubPostProcessingSettings::class.java)
        return GitHubPostProcessingSettings(
            config = values.getString(GitHubPostProcessingSettings::config, ""),
            repository = values.getString(GitHubPostProcessingSettings::repository, ""),
            workflow = values.getString(GitHubPostProcessingSettings::workflow, ""),
            branch = values.getString(
                GitHubPostProcessingSettings::branch,
                GitHubPostProcessingSettings.DEFAULT_BRANCH
            ),
            retries = values.getInt(
                GitHubPostProcessingSettings::retries,
                GitHubPostProcessingSettings.DEFAULT_RETRIES
            ),
            retriesDelaySeconds = values.getInt(
                GitHubPostProcessingSettings::retriesDelaySeconds,
                GitHubPostProcessingSettings.DEFAULT_RETRIES_DELAY_SECONDS
            ),
        )
    }

    override fun getSettingsClass(): Class<GitHubPostProcessingSettings> = GitHubPostProcessingSettings::class.java
}