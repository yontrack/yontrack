package net.nemerosa.ontrack.extension.bitbucket.cloud.settings

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.getBoolean
import net.nemerosa.ontrack.model.support.getEnum
import net.nemerosa.ontrack.model.support.getInt
import net.nemerosa.ontrack.model.support.getLong
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
        mergeStrategy = settingsRepository.getEnum(
            BitbucketCloudSettings::mergeStrategy,
            BitbucketCloudSettings.DEFAULT_MERGE_STRATEGY
        ),
        autoMergeTimeout = settingsRepository.getLong(
            BitbucketCloudSettings::autoMergeTimeout,
            BitbucketCloudSettings.DEFAULT_AUTO_MERGE_TIMEOUT
        ),
        autoMergeInterval = settingsRepository.getLong(
            BitbucketCloudSettings::autoMergeInterval,
            BitbucketCloudSettings.DEFAULT_AUTO_MERGE_INTERVAL
        ),
        autoDeleteBranch = settingsRepository.getBoolean(
            BitbucketCloudSettings::autoDeleteBranch,
            BitbucketCloudSettings.DEFAULT_AUTO_DELETE_BRANCH
        ),
    )

    override fun getSettingsClass(): Class<BitbucketCloudSettings> = BitbucketCloudSettings::class.java
}
