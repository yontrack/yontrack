package net.nemerosa.ontrack.extension.bitbucket.cloud.autoversioning

import net.nemerosa.ontrack.extension.casc.context.settings.AbstractSubSettingsContext
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.SettingsManagerService
import org.springframework.stereotype.Component

@Component
class BitbucketCloudPostProcessingSettingsCasc(
    settingsManagerService: SettingsManagerService,
    cachedSettingsService: CachedSettingsService,
) : AbstractSubSettingsContext<BitbucketCloudPostProcessingSettings>(
    BitbucketCloudPostProcessingSettingsManager.ID,
    BitbucketCloudPostProcessingSettings::class,
    settingsManagerService,
    cachedSettingsService,
)
