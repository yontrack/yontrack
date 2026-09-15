package net.nemerosa.ontrack.extension.bitbucket.cloud.settings

import net.nemerosa.ontrack.extension.casc.context.settings.AbstractSubSettingsContext
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.SettingsManagerService
import org.springframework.stereotype.Component

/**
 * CasC for the [Bitbucket Cloud settings][BitbucketCloudSettings], under `ontrack.config.settings.bitbucket-cloud`.
 */
@Component
class BitbucketCloudSettingsCasc(
    settingsManagerService: SettingsManagerService,
    cachedSettingsService: CachedSettingsService,
) : AbstractSubSettingsContext<BitbucketCloudSettings>(
    "bitbucket-cloud",
    BitbucketCloudSettings::class,
    settingsManagerService,
    cachedSettingsService,
)
