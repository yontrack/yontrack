package net.nemerosa.ontrack.extension.notifications.http.settings

import net.nemerosa.ontrack.extension.casc.context.settings.AbstractSubSettingsContext
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.SettingsManagerService
import org.springframework.stereotype.Component

@Component
class HttpNotificationSettingsCascContext(
    settingsManagerService: SettingsManagerService,
    cachedSettingsService: CachedSettingsService,
) : AbstractSubSettingsContext<HttpNotificationSettings>(
    "http-notifications",
    HttpNotificationSettings::class,
    settingsManagerService,
    cachedSettingsService,
)
