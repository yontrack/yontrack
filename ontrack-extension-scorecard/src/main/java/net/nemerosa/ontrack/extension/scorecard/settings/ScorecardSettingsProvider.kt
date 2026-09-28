package net.nemerosa.ontrack.extension.scorecard.settings

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.getInt
import net.nemerosa.ontrack.model.support.getString
import org.springframework.stereotype.Component

@Component
class ScorecardSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<ScorecardSettings> {

    override fun getSettings() = ScorecardSettings(
        windowDays = settingsRepository.getInt(
            ScorecardSettings::windowDays,
            ScorecardSettings.DEFAULT_WINDOW_DAYS
        ),
        retentionDays = settingsRepository.getInt(
            ScorecardSettings::retentionDays,
            ScorecardSettings.DEFAULT_RETENTION_DAYS
        ),
        cron = settingsRepository.getString(
            ScorecardSettings::cron,
            ScorecardSettings.DEFAULT_CRON
        ),
    )

    override fun getSettingsClass(): Class<ScorecardSettings> = ScorecardSettings::class.java
}
