package net.nemerosa.ontrack.extension.scorecard.settings

import net.nemerosa.ontrack.extension.casc.context.settings.AbstractSubSettingsContext
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.SettingsManagerService
import org.springframework.stereotype.Component

/**
 * CasC for the [settings of the delivery scorecard][ScorecardSettings], under `ontrack.config.settings.delivery-scorecard`.
 */
@Component
class ScorecardSettingsCascContext(
    settingsManagerService: SettingsManagerService,
    cachedSettingsService: CachedSettingsService,
) : AbstractSubSettingsContext<ScorecardSettings>(
    "delivery-scorecard",
    ScorecardSettings::class,
    settingsManagerService,
    cachedSettingsService,
)
