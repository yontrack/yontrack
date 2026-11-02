package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.extension.casc.context.settings.AbstractSubSettingsContext
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.SettingsManagerService
import org.springframework.stereotype.Component

@Component
class NotGrantedMedalsSettingsContext(
    settingsManagerService: SettingsManagerService,
    cachedSettingsService: CachedSettingsService,
) : AbstractSubSettingsContext<NotGrantedMedalsSettings>(
    "not-granted-medals",
    NotGrantedMedalsSettings::class,
    settingsManagerService,
    cachedSettingsService,
)
