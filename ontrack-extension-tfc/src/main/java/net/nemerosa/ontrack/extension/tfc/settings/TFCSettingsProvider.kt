package net.nemerosa.ontrack.extension.tfc.settings

import net.nemerosa.ontrack.model.security.EncryptionService
import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.values
import net.nemerosa.ontrack.model.support.getBoolean
import net.nemerosa.ontrack.model.support.getPassword
import org.springframework.stereotype.Component

@Component
class TFCSettingsProvider(
    private val settingsRepository: SettingsRepository,
    private val encryptionService: EncryptionService,
) : SettingsProvider<TFCSettings> {

    override fun getSettings(): TFCSettings {
        val values = settingsRepository.values(TFCSettings::class.java)
        return TFCSettings(
            enabled = values.getBoolean(TFCSettings::enabled, false),
            token = values.getPassword(TFCSettings::token, "", encryptionService::decrypt)
        )
    }

    override fun getSettingsClass(): Class<TFCSettings> = TFCSettings::class.java
}