package net.nemerosa.ontrack.extension.gitlab.settings

import net.nemerosa.ontrack.extension.casc.context.settings.AbstractSubSettingsContext
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.SettingsManagerService
import org.springframework.stereotype.Component

/**
 * CasC for the [GitLab settings][GitLabSettings], under `ontrack.config.settings.gitlab`.
 */
@Component
class GitLabSettingsCasc(
    settingsManagerService: SettingsManagerService,
    cachedSettingsService: CachedSettingsService,
) : AbstractSubSettingsContext<GitLabSettings>(
    "gitlab",
    GitLabSettings::class,
    settingsManagerService,
    cachedSettingsService,
)
