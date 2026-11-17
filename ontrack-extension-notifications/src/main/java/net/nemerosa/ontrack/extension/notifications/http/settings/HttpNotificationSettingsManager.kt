package net.nemerosa.ontrack.extension.notifications.http.settings

import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.AbstractSettingsManager
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.setInt
import org.springframework.stereotype.Component

@Component
class HttpNotificationSettingsManager(
    cachedSettingsService: CachedSettingsService,
    securityService: SecurityService,
    private val settingsRepository: SettingsRepository,
) : AbstractSettingsManager<HttpNotificationSettings>(
    HttpNotificationSettings::class.java,
    cachedSettingsService,
    securityService,
) {

    override fun doSaveSettings(settings: HttpNotificationSettings) {
        checkPositive("maximum timeout", settings.maxTimeoutSeconds)
        checkPositive("maximum response size", settings.maxResponseBytes)
        checkPositive("request timeout", settings.requestTimeoutSeconds)
        settingsRepository.setInt<HttpNotificationSettings>(settings::maxTimeoutSeconds)
        settingsRepository.setInt<HttpNotificationSettings>(settings::maxResponseBytes)
        settingsRepository.setInt<HttpNotificationSettings>(settings::requestTimeoutSeconds)
    }

    private fun checkPositive(name: String, value: Int) {
        if (value <= 0) {
            throw HttpNotificationSettingsException("The $name must be strictly positive: $value.")
        }
    }

    override fun getId(): String = "http-notifications"

    override fun getTitle(): String = "HTTP notifications"
}

class HttpNotificationSettingsException(message: String) : InputException(message)
