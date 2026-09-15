package net.nemerosa.ontrack.extension.bitbucket.cloud.autoversioning

import net.nemerosa.ontrack.extension.bitbucket.cloud.BitbucketCloudExtensionFeature
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines.BitbucketPipelinesService
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.springframework.stereotype.Component

/**
 * Auto-versioning post-processing through a Bitbucket pipeline.
 */
@Component
class BitbucketCloudPostProcessing(
    extensionFeature: BitbucketCloudExtensionFeature,
    cachedSettingsService: CachedSettingsService,
    bitbucketCloudConfigurationService: BitbucketCloudConfigurationService,
    bitbucketPipelinesService: BitbucketPipelinesService,
) : AbstractBitbucketCloudPostProcessing(
    extensionFeature = extensionFeature,
    cachedSettingsService = cachedSettingsService,
    bitbucketCloudConfigurationService = bitbucketCloudConfigurationService,
    bitbucketPipelinesService = bitbucketPipelinesService,
) {

    override val id: String = "bitbucket-cloud"

    override val name: String = "Bitbucket pipeline post processing"
}
