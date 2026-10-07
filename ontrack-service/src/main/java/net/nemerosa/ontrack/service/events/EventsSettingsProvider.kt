package net.nemerosa.ontrack.service.events

import net.nemerosa.ontrack.model.settings.EventsSettings
import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.getInt
import org.springframework.stereotype.Component

@Component
class EventsSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<EventsSettings> {

    override fun getSettings() = EventsSettings(
        retentionDays = settingsRepository.getInt(
            EventsSettings::retentionDays,
            EventsSettings.DEFAULT_RETENTION_DAYS
        ),
    )

    override fun getSettingsClass(): Class<EventsSettings> = EventsSettings::class.java
}
