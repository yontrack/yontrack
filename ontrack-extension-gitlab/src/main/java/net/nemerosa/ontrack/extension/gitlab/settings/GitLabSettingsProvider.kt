package net.nemerosa.ontrack.extension.gitlab.settings

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.values
import net.nemerosa.ontrack.model.support.getBoolean
import net.nemerosa.ontrack.model.support.getInt
import net.nemerosa.ontrack.model.support.getLong
import org.springframework.stereotype.Component

@Component
class GitLabSettingsProvider(
    private val settingsRepository: SettingsRepository,
) : SettingsProvider<GitLabSettings> {

    override fun getSettings(): GitLabSettings {
        val values = settingsRepository.values(GitLabSettings::class.java)
        return GitLabSettings(
            maxCommits = values.getInt(
                GitLabSettings::maxCommits,
                GitLabSettings.DEFAULT_MAX_COMMITS
            ),
            squash = values.getBoolean(
                GitLabSettings::squash,
                GitLabSettings.DEFAULT_SQUASH
            ),
            removeSourceBranch = values.getBoolean(
                GitLabSettings::removeSourceBranch,
                GitLabSettings.DEFAULT_REMOVE_SOURCE_BRANCH
            ),
            autoMergeTimeout = values.getLong(
                GitLabSettings::autoMergeTimeout,
                GitLabSettings.DEFAULT_AUTO_MERGE_TIMEOUT
            ),
            autoMergeInterval = values.getLong(
                GitLabSettings::autoMergeInterval,
                GitLabSettings.DEFAULT_AUTO_MERGE_INTERVAL
            ),
        )
    }

    override fun getSettingsClass(): Class<GitLabSettings> = GitLabSettings::class.java
}
