package net.nemerosa.ontrack.extension.bitbucket.cloud.mock

import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.notifications.AbstractBitbucketPipelinesNotificationChannel
import net.nemerosa.ontrack.extension.bitbucket.cloud.notifications.BitbucketPipelinesNotificationChannelConfig
import net.nemerosa.ontrack.extension.notifications.channels.NoTemplate
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.events.EventTemplatingService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Same configuration as the `bitbucket-pipelines` channel, but the pipelines are only recorded, and their outcome is
 * driven by the `MOCK_RESULT` and `MOCK_DURATION_SECONDS` variables. See [MockBitbucketPipelineRun].
 */
@Component
@Profile(RunProfile.DEV)
@Documentation(BitbucketPipelinesNotificationChannelConfig::class)
@NoTemplate
class MockBitbucketPipelinesNotificationChannel(
    bitbucketCloudConfigurationService: BitbucketCloudConfigurationService,
    eventTemplatingService: EventTemplatingService,
    mockBitbucketPipelinesRecorder: MockBitbucketPipelinesRecorder,
) : AbstractBitbucketPipelinesNotificationChannel(
    bitbucketCloudConfigurationService = bitbucketCloudConfigurationService,
    eventTemplatingService = eventTemplatingService,
    bitbucketPipelinesService = MockBitbucketPipelinesService(mockBitbucketPipelinesRecorder),
) {

    override val type: String = "mock-bitbucket-pipelines"
    override val displayName: String = "Mock Bitbucket Pipelines"

}
