package net.nemerosa.ontrack.extension.bitbucket.cloud.settings

import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.AbstractSettingsManager
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.support.SettingsRepository
import net.nemerosa.ontrack.model.support.setInt
import org.springframework.stereotype.Component

@Component
class BitbucketCloudSettingsManager(
    cachedSettingsService: CachedSettingsService,
    securityService: SecurityService,
    private val settingsRepository: SettingsRepository,
) : AbstractSettingsManager<BitbucketCloudSettings>(
    BitbucketCloudSettings::class.java,
    cachedSettingsService,
    securityService,
) {

    override fun doSaveSettings(settings: BitbucketCloudSettings) {
        settingsRepository.setInt<BitbucketCloudSettings>(settings::maxCommits)
    }

    override fun getId(): String = "bitbucket-cloud"

    override fun getTitle(): String = "Bitbucket Cloud"
}
