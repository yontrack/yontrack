package net.nemerosa.ontrack.extension.gitlab.autoversioning

import net.nemerosa.ontrack.extension.casc.context.settings.AbstractSubSettingsContext
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.SettingsManagerService
import org.springframework.stereotype.Component

/**
 * CasC for the [GitLab post-processing settings][GitLabPostProcessingSettings], under
 * `ontrack.config.settings.gitlab-av-post-processing`.
 */
@Component
class GitLabPostProcessingSettingsCasc(
    settingsManagerService: SettingsManagerService,
    cachedSettingsService: CachedSettingsService,
) : AbstractSubSettingsContext<GitLabPostProcessingSettings>(
    GitLabPostProcessingSettingsManager.ID,
    GitLabPostProcessingSettings::class,
    settingsManagerService,
    cachedSettingsService,
)
