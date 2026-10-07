package net.nemerosa.ontrack.extension.casc.context.settings

import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.EventsSettings
import net.nemerosa.ontrack.model.settings.SettingsManagerService
import org.springframework.stereotype.Component

@Component
class EventsSettingsCasc(
    settingsManagerService: SettingsManagerService,
    cachedSettingsService: CachedSettingsService
) : AbstractSubSettingsContext<EventsSettings>(
    "events",
    EventsSettings::class,
    settingsManagerService,
    cachedSettingsService
)
