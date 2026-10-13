package net.nemerosa.ontrack.extension.scorecard.settings

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.values
import net.nemerosa.ontrack.model.support.getInt
import net.nemerosa.ontrack.model.support.getString
import org.springframework.stereotype.Component

@Component
class ScorecardSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<ScorecardSettings> {

    override fun getSettings(): ScorecardSettings {
        val values = settingsRepository.values(ScorecardSettings::class.java)
        return ScorecardSettings(
            windowDays = values.getInt(
                ScorecardSettings::windowDays,
                ScorecardSettings.DEFAULT_WINDOW_DAYS
            ),
            retentionDays = values.getInt(
                ScorecardSettings::retentionDays,
                ScorecardSettings.DEFAULT_RETENTION_DAYS
            ),
            cron = values.getString(
                ScorecardSettings::cron,
                ScorecardSettings.DEFAULT_CRON
            ),
            securityFreshnessDays = values.getInt(
                ScorecardSettings::securityFreshnessDays,
                ScorecardSettings.DEFAULT_SECURITY_FRESHNESS_DAYS
            ),
        )
    }

    override fun getSettingsClass(): Class<ScorecardSettings> = ScorecardSettings::class.java
}
