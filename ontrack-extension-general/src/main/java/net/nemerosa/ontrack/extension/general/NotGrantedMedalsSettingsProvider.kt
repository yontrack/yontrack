package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.values
import org.springframework.stereotype.Component

@Component
class NotGrantedMedalsSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<NotGrantedMedalsSettings> {

    override fun getSettings(): NotGrantedMedalsSettings {
        val values = settingsRepository.values(NotGrantedMedalsSettings::class.java)
        return NotGrantedMedalsSettings(
            enabled = values.getBoolean(NotGrantedMedalsSettings::class.java, NotGrantedMedalsSettings::enabled.name, false),
        )
    }

    override fun getSettingsClass(): Class<NotGrantedMedalsSettings> =
        NotGrantedMedalsSettings::class.java
}
