package net.nemerosa.ontrack.extension.scorecard.settings

import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.AbstractSettingsManager
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.setInt
import net.nemerosa.ontrack.model.support.setString
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Component

@Component
class ScorecardSettingsManager(
    cachedSettingsService: CachedSettingsService,
    securityService: SecurityService,
    private val settingsRepository: SettingsRepository,
) : AbstractSettingsManager<ScorecardSettings>(
    ScorecardSettings::class.java,
    cachedSettingsService,
    securityService
) {

    override fun doSaveSettings(settings: ScorecardSettings) {
        if (settings.windowDays < 1) {
            throw ScorecardSettingsException("The window must be one day at least.")
        }
        if (settings.retentionDays < 1) {
            throw ScorecardSettingsException("The retention must be one day at least.")
        }
        if (!CronExpression.isValidExpression(settings.cron)) {
            throw ScorecardSettingsException("The schedule is not a valid cron expression: ${settings.cron}")
        }
        settingsRepository.setInt<ScorecardSettings>(settings::windowDays)
        settingsRepository.setInt<ScorecardSettings>(settings::retentionDays)
        settingsRepository.setString<ScorecardSettings>(settings::cron)
    }

    override fun getId(): String = "delivery-scorecard"

    override fun getTitle(): String = "Delivery scorecard"
}

class ScorecardSettingsException(message: String) : InputException(message)
