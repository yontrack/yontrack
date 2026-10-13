package net.nemerosa.ontrack.extension.gitlab.autoversioning

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.values
import net.nemerosa.ontrack.model.support.getInt
import net.nemerosa.ontrack.model.support.getString
import org.springframework.stereotype.Component

@Component
class GitLabPostProcessingSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<GitLabPostProcessingSettings> {

    override fun getSettings(): GitLabPostProcessingSettings {
        val values = settingsRepository.values(GitLabPostProcessingSettings::class.java)
        return GitLabPostProcessingSettings(
            config = values.getString(GitLabPostProcessingSettings::config, ""),
            project = values.getString(GitLabPostProcessingSettings::project, ""),
            ref = values.getString(
                GitLabPostProcessingSettings::ref,
                GitLabPostProcessingSettings.DEFAULT_REF
            ),
            retries = values.getInt(
                GitLabPostProcessingSettings::retries,
                GitLabPostProcessingSettings.DEFAULT_RETRIES
            ),
            retriesDelaySeconds = values.getInt(
                GitLabPostProcessingSettings::retriesDelaySeconds,
                GitLabPostProcessingSettings.DEFAULT_RETRIES_DELAY_SECONDS
            ),
        )
    }

    override fun getSettingsClass(): Class<GitLabPostProcessingSettings> =
        GitLabPostProcessingSettings::class.java
}
