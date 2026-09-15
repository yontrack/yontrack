package net.nemerosa.ontrack.extension.bitbucket.cloud.autoversioning

import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.AbstractSettingsManager
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.setInt
import net.nemerosa.ontrack.model.support.setString
import org.springframework.stereotype.Component

@Component
class BitbucketCloudPostProcessingSettingsManager(
    cachedSettingsService: CachedSettingsService,
    securityService: SecurityService,
    private val settingsRepository: SettingsRepository,
) : AbstractSettingsManager<BitbucketCloudPostProcessingSettings>(
    BitbucketCloudPostProcessingSettings::class.java,
    cachedSettingsService,
    securityService
) {
    override fun doSaveSettings(settings: BitbucketCloudPostProcessingSettings) {
        settingsRepository.setString<BitbucketCloudPostProcessingSettings>(settings::config)
        settingsRepository.setString<BitbucketCloudPostProcessingSettings>(settings::workspace)
        settingsRepository.setString<BitbucketCloudPostProcessingSettings>(settings::repository)
        settingsRepository.setString<BitbucketCloudPostProcessingSettings>(settings::pipeline)
        settingsRepository.setString<BitbucketCloudPostProcessingSettings>(settings::branch)
        settingsRepository.setInt<BitbucketCloudPostProcessingSettings>(settings::retries)
        settingsRepository.setInt<BitbucketCloudPostProcessingSettings>(settings::retriesDelaySeconds)
    }

    override fun getId(): String = ID

    override fun getTitle(): String = "Bitbucket Cloud Auto Versioning Post Processing"

    companion object {
        const val ID = "bitbucket-cloud-av-post-processing"
    }
}
