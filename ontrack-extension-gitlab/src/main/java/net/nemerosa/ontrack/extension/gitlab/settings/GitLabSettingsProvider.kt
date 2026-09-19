package net.nemerosa.ontrack.extension.gitlab.settings

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.getBoolean
import net.nemerosa.ontrack.model.support.getInt
import net.nemerosa.ontrack.model.support.getLong
import org.springframework.stereotype.Component

@Component
class GitLabSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<GitLabSettings> {

    override fun getSettings() = GitLabSettings(
        maxCommits = settingsRepository.getInt(
            GitLabSettings::maxCommits,
            GitLabSettings.DEFAULT_MAX_COMMITS
        ),
        squash = settingsRepository.getBoolean(
            GitLabSettings::squash,
            GitLabSettings.DEFAULT_SQUASH
        ),
        removeSourceBranch = settingsRepository.getBoolean(
            GitLabSettings::removeSourceBranch,
            GitLabSettings.DEFAULT_REMOVE_SOURCE_BRANCH
        ),
        autoMergeTimeout = settingsRepository.getLong(
            GitLabSettings::autoMergeTimeout,
            GitLabSettings.DEFAULT_AUTO_MERGE_TIMEOUT
        ),
        autoMergeInterval = settingsRepository.getLong(
            GitLabSettings::autoMergeInterval,
            GitLabSettings.DEFAULT_AUTO_MERGE_INTERVAL
        ),
    )

    override fun getSettingsClass(): Class<GitLabSettings> = GitLabSettings::class.java
}
