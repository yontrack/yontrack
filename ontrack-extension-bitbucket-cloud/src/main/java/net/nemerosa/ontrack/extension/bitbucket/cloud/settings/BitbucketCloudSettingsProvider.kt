package net.nemerosa.ontrack.extension.bitbucket.cloud.settings

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.getInt
import org.springframework.stereotype.Component

@Component
class BitbucketCloudSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<BitbucketCloudSettings> {

    override fun getSettings() = BitbucketCloudSettings(
        maxCommits = settingsRepository.getInt(
            BitbucketCloudSettings::maxCommits,
            BitbucketCloudSettings.DEFAULT_MAX_COMMITS
        ),
    )

    override fun getSettingsClass(): Class<BitbucketCloudSettings> = BitbucketCloudSettings::class.java
}
