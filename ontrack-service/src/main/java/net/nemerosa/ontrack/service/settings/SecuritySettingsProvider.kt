package net.nemerosa.ontrack.service.settings

import net.nemerosa.ontrack.model.settings.SecuritySettings
import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.values
import org.springframework.stereotype.Component

@Component
class SecuritySettingsProvider(
        private val settingsRepository: SettingsRepository
) : SettingsProvider<SecuritySettings> {
    /**
     * By default, grants view accesses to everybody.
     */
    override fun getSettings(): SecuritySettings {
        val values = settingsRepository.values(SecuritySettings::class.java)
        return SecuritySettings(
                values.getBoolean(SecuritySettings::class.java, "grantProjectViewToAll", true),
                values.getBoolean(SecuritySettings::class.java, "grantProjectParticipationToAll", true),
                values.getBoolean(SecuritySettings::class.java, SecuritySettings::builtInAuthenticationEnabled.name, SecuritySettings.DEFAULT_BUILTIN_AUTHENTICATION_ENABLED),
                values.getBoolean(SecuritySettings::class.java, SecuritySettings::grantDashboardEditionToAll.name, SecuritySettings.DEFAULT_GRANT_DASHBOARD_EDITION),
                values.getBoolean(SecuritySettings::class.java, SecuritySettings::grantDashboardSharingToAll.name, SecuritySettings.DEFAULT_GRANT_DASHBOARD_SHARING),
        )
    }

    override fun getSettingsClass(): Class<SecuritySettings> = SecuritySettings::class.java

}