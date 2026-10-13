package net.nemerosa.ontrack.extension.notifications.webhooks

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.values
import net.nemerosa.ontrack.model.support.getBoolean
import net.nemerosa.ontrack.model.support.getInt
import org.springframework.stereotype.Component

/**
 * Reading the webhook settings.
 */
@Component
class WebhookSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<WebhookSettings> {

    override fun getSettings(): WebhookSettings {
        val values = settingsRepository.values(WebhookSettings::class.java)
        return WebhookSettings(
            enabled = values.getBoolean(WebhookSettings::enabled, false),
            timeoutMinutes = values.getInt(WebhookSettings::timeoutMinutes, 5),
            deliveriesRetentionDays = values.getInt(WebhookSettings::deliveriesRetentionDays, 30),
        )
    }

    override fun getSettingsClass(): Class<WebhookSettings> = WebhookSettings::class.java
}
