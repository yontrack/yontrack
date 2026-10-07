package net.nemerosa.ontrack.service.events

import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.AbstractSettingsManager
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.EventsSettings
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.setInt
import org.springframework.stereotype.Component

@Component
class EventsSettingsManager(
    cachedSettingsService: CachedSettingsService,
    securityService: SecurityService,
    private val settingsRepository: SettingsRepository,
) : AbstractSettingsManager<EventsSettings>(
    EventsSettings::class.java,
    cachedSettingsService,
    securityService
) {

    override fun doSaveSettings(settings: EventsSettings) {
        if (settings.retentionDays < 0) {
            throw EventsSettingsException("The retention of the events cannot be negative: ${settings.retentionDays}. Use 0 to keep the events forever.")
        }
        settingsRepository.setInt<EventsSettings>(settings::retentionDays)
    }

    override fun getId(): String = "events"

    override fun getTitle(): String = "Events"
}

class EventsSettingsException(message: String) : InputException(message)
