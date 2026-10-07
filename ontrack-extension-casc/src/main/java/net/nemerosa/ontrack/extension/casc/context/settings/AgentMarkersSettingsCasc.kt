package net.nemerosa.ontrack.extension.casc.context.settings

import net.nemerosa.ontrack.extension.scm.changelog.assistants.AgentMarkersSettings
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.SettingsManagerService
import org.springframework.stereotype.Component

/**
 * CasC for the [agent markers settings][AgentMarkersSettings], under `ontrack.config.settings.agent-markers`.
 */
@Component
class AgentMarkersSettingsCasc(
    settingsManagerService: SettingsManagerService,
    cachedSettingsService: CachedSettingsService
) : AbstractSubSettingsContext<AgentMarkersSettings>(
    "agent-markers",
    AgentMarkersSettings::class,
    settingsManagerService,
    cachedSettingsService
)
