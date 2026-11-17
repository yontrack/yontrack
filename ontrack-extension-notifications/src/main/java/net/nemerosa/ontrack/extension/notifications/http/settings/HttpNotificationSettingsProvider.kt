package net.nemerosa.ontrack.extension.notifications.http.settings

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.getInt
import net.nemerosa.ontrack.model.support.values
import org.springframework.stereotype.Component

@Component
class HttpNotificationSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<HttpNotificationSettings> {

    override fun getSettings(): HttpNotificationSettings {
        val values = settingsRepository.values(HttpNotificationSettings::class.java)
        return HttpNotificationSettings(
            maxTimeoutSeconds = values.getInt(
                HttpNotificationSettings::maxTimeoutSeconds,
                HttpNotificationSettings.DEFAULT_MAX_TIMEOUT_SECONDS
            ),
            maxResponseBytes = values.getInt(
                HttpNotificationSettings::maxResponseBytes,
                HttpNotificationSettings.DEFAULT_MAX_RESPONSE_BYTES
            ),
            requestTimeoutSeconds = values.getInt(
                HttpNotificationSettings::requestTimeoutSeconds,
                HttpNotificationSettings.DEFAULT_REQUEST_TIMEOUT_SECONDS
            ),
        )
    }

    override fun getSettingsClass(): Class<HttpNotificationSettings> = HttpNotificationSettings::class.java
}
