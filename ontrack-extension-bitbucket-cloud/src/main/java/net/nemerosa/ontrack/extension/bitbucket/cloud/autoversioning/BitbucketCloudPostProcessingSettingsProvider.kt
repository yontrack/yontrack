package net.nemerosa.ontrack.extension.bitbucket.cloud.autoversioning

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.values
import net.nemerosa.ontrack.model.support.getInt
import net.nemerosa.ontrack.model.support.getString
import org.springframework.stereotype.Component

@Component
class BitbucketCloudPostProcessingSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<BitbucketCloudPostProcessingSettings> {

    override fun getSettings(): BitbucketCloudPostProcessingSettings {
        val values = settingsRepository.values(BitbucketCloudPostProcessingSettings::class.java)
        return BitbucketCloudPostProcessingSettings(
            config = values.getString(BitbucketCloudPostProcessingSettings::config, ""),
            workspace = values.getString(BitbucketCloudPostProcessingSettings::workspace, ""),
            repository = values.getString(BitbucketCloudPostProcessingSettings::repository, ""),
            pipeline = values.getString(BitbucketCloudPostProcessingSettings::pipeline, ""),
            branch = values.getString(
                BitbucketCloudPostProcessingSettings::branch,
                BitbucketCloudPostProcessingSettings.DEFAULT_BRANCH
            ),
            retries = values.getInt(
                BitbucketCloudPostProcessingSettings::retries,
                BitbucketCloudPostProcessingSettings.DEFAULT_RETRIES
            ),
            retriesDelaySeconds = values.getInt(
                BitbucketCloudPostProcessingSettings::retriesDelaySeconds,
                BitbucketCloudPostProcessingSettings.DEFAULT_RETRIES_DELAY_SECONDS
            ),
        )
    }

    override fun getSettingsClass(): Class<BitbucketCloudPostProcessingSettings> =
        BitbucketCloudPostProcessingSettings::class.java
}
