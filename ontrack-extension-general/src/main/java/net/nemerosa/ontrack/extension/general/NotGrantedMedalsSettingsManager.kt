package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.AbstractSettingsManager
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.support.SettingsRepository
import org.springframework.stereotype.Component

@Component
class NotGrantedMedalsSettingsManager(
    cachedSettingsService: CachedSettingsService,
    securityService: SecurityService,
    private val settingsRepository: SettingsRepository,
) : AbstractSettingsManager<NotGrantedMedalsSettings>(
    NotGrantedMedalsSettings::class.java,
    cachedSettingsService,
    securityService,
) {

    override fun getId(): String = "not-granted-medals"

    override fun getTitle(): String = "Not-granted medals"

    override fun doSaveSettings(settings: NotGrantedMedalsSettings?) {
        if (settings != null) {
            settingsRepository.setBoolean(
                NotGrantedMedalsSettings::class.java,
                NotGrantedMedalsSettings::enabled.name,
                settings.enabled,
            )
        }
    }
}
