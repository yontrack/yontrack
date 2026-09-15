package net.nemerosa.ontrack.extension.bitbucket.cloud.autoversioning

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.getInt
import net.nemerosa.ontrack.model.support.getString
import org.springframework.stereotype.Component

@Component
class BitbucketCloudPostProcessingSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<BitbucketCloudPostProcessingSettings> {

    override fun getSettings() = BitbucketCloudPostProcessingSettings(
        config = settingsRepository.getString(BitbucketCloudPostProcessingSettings::config, ""),
        workspace = settingsRepository.getString(BitbucketCloudPostProcessingSettings::workspace, ""),
        repository = settingsRepository.getString(BitbucketCloudPostProcessingSettings::repository, ""),
        pipeline = settingsRepository.getString(BitbucketCloudPostProcessingSettings::pipeline, ""),
        branch = settingsRepository.getString(
            BitbucketCloudPostProcessingSettings::branch,
            BitbucketCloudPostProcessingSettings.DEFAULT_BRANCH
        ),
        retries = settingsRepository.getInt(
            BitbucketCloudPostProcessingSettings::retries,
            BitbucketCloudPostProcessingSettings.DEFAULT_RETRIES
        ),
        retriesDelaySeconds = settingsRepository.getInt(
            BitbucketCloudPostProcessingSettings::retriesDelaySeconds,
            BitbucketCloudPostProcessingSettings.DEFAULT_RETRIES_DELAY_SECONDS
        ),
    )

    override fun getSettingsClass(): Class<BitbucketCloudPostProcessingSettings> =
        BitbucketCloudPostProcessingSettings::class.java
}
