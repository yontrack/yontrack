package net.nemerosa.ontrack.extension.gitlab.settings

import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.AbstractSettingsManager
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.setInt
import org.springframework.stereotype.Component

@Component
class GitLabSettingsManager(
    cachedSettingsService: CachedSettingsService,
    securityService: SecurityService,
    private val settingsRepository: SettingsRepository,
) : AbstractSettingsManager<GitLabSettings>(
    GitLabSettings::class.java,
    cachedSettingsService,
    securityService,
) {

    override fun doSaveSettings(settings: GitLabSettings) {
        settingsRepository.setInt<GitLabSettings>(settings::maxCommits)
    }

    override fun getId(): String = "gitlab"

    override fun getTitle(): String = "GitLab"
}
