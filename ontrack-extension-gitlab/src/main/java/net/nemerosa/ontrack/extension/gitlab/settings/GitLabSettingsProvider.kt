package net.nemerosa.ontrack.extension.gitlab.settings

import net.nemerosa.ontrack.model.settings.SettingsProvider
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.getInt
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
    )

    override fun getSettingsClass(): Class<GitLabSettings> = GitLabSettings::class.java
}
