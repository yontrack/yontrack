package net.nemerosa.ontrack.extension.gitlab.autoversioning

import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.AbstractSettingsManager
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.setInt
import net.nemerosa.ontrack.model.support.setString
import org.springframework.stereotype.Component

@Component
class GitLabPostProcessingSettingsManager(
    cachedSettingsService: CachedSettingsService,
    securityService: SecurityService,
    private val settingsRepository: SettingsRepository,
) : AbstractSettingsManager<GitLabPostProcessingSettings>(
    GitLabPostProcessingSettings::class.java,
    cachedSettingsService,
    securityService,
) {
    override fun doSaveSettings(settings: GitLabPostProcessingSettings) {
        settingsRepository.setString<GitLabPostProcessingSettings>(settings::config)
        settingsRepository.setString<GitLabPostProcessingSettings>(settings::project)
        settingsRepository.setString<GitLabPostProcessingSettings>(settings::ref)
        settingsRepository.setInt<GitLabPostProcessingSettings>(settings::retries)
        settingsRepository.setInt<GitLabPostProcessingSettings>(settings::retriesDelaySeconds)
    }

    override fun getId(): String = ID

    override fun getTitle(): String = "GitLab Auto Versioning Post Processing"

    companion object {
        const val ID = "gitlab-av-post-processing"
    }
}
