package net.nemerosa.ontrack.extension.bitbucket.cloud.mock

import net.nemerosa.ontrack.extension.bitbucket.cloud.BitbucketCloudExtensionFeature
import net.nemerosa.ontrack.extension.bitbucket.cloud.autoversioning.AbstractBitbucketCloudPostProcessing
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.common.RunProfile
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Same configuration and settings as the `bitbucket-cloud` post-processing, but the pipelines are only recorded
 * by the [MockBitbucketPipelinesRecorder], for the acceptance tests. Nothing is committed to the upgrade branch.
 */
@Component
@Profile(RunProfile.DEV)
class MockBitbucketCloudPostProcessing(
    extensionFeature: BitbucketCloudExtensionFeature,
    cachedSettingsService: CachedSettingsService,
    bitbucketCloudConfigurationService: BitbucketCloudConfigurationService,
    mockBitbucketPipelinesRecorder: MockBitbucketPipelinesRecorder,
) : AbstractBitbucketCloudPostProcessing(
    extensionFeature = extensionFeature,
    cachedSettingsService = cachedSettingsService,
    bitbucketCloudConfigurationService = bitbucketCloudConfigurationService,
    bitbucketPipelinesService = MockBitbucketPipelinesService(mockBitbucketPipelinesRecorder),
) {

    override val id: String = "mock-bitbucket-cloud"

    override val name: String = "Mock Bitbucket pipeline post processing"
}
