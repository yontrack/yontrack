package net.nemerosa.ontrack.extension.av.settings

import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.AbstractSettingsManager
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.setBoolean
import org.springframework.stereotype.Component

@Component
class AutoVersioningSettingsManager(
    cachedSettingsService: CachedSettingsService,
    securityService: SecurityService,
    private val settingsRepository: SettingsRepository,
) : AbstractSettingsManager<AutoVersioningSettings>(
    AutoVersioningSettings::class.java,
    cachedSettingsService,
    securityService
) {

    override fun doSaveSettings(settings: AutoVersioningSettings) {
        settingsRepository.setBoolean<AutoVersioningSettings>(settings::enabled)

        // Audit retention days
        settingsRepository.setString(
            AutoVersioningSettings::class.java,
            AutoVersioningSettings::auditRetentionDuration.name,
            settings.auditRetentionDuration.toString()
        )

        // Audit cleanup days
        settingsRepository.setString(
            AutoVersioningSettings::class.java,
            AutoVersioningSettings::auditCleanupDuration.name,
            settings.auditCleanupDuration.toString()
        )

        // Build links
        settingsRepository.setBoolean<AutoVersioningSettings>(settings::buildLinks)

        // Automatic retries
        settingsRepository.setInt(
            AutoVersioningSettings::class.java,
            AutoVersioningSettings::retryMaxCount.name,
            settings.retryMaxCount
        )
        settingsRepository.setInt(
            AutoVersioningSettings::class.java,
            AutoVersioningSettings::retryDelayMinutes.name,
            settings.retryDelayMinutes
        )
    }

    override fun getId(): String = "auto-versioning"

    override fun getTitle(): String = "Auto Versioning"
}