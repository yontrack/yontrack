package net.nemerosa.ontrack.extension.bitbucket.cloud.notifications

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines.BitbucketPipelinesService
import net.nemerosa.ontrack.extension.notifications.channels.NoTemplate
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.docs.DocumentationLink
import net.nemerosa.ontrack.model.events.EventTemplatingService
import org.springframework.stereotype.Component

@APIDescription("This channel is used to trigger Bitbucket pipelines on a branch, with some variables.")
@Documentation(BitbucketPipelinesNotificationChannelConfig::class)
@Documentation(BitbucketPipelinesNotificationChannelOutput::class, section = "output")
@DocumentationLink(value = "integrations/notifications/bitbucket-pipelines.md", name = "Bitbucket Pipelines")
@NoTemplate
@Component
class BitbucketPipelinesNotificationChannel(
    bitbucketCloudConfigurationService: BitbucketCloudConfigurationService,
    eventTemplatingService: EventTemplatingService,
    bitbucketPipelinesService: BitbucketPipelinesService,
) : AbstractBitbucketPipelinesNotificationChannel(
    bitbucketCloudConfigurationService = bitbucketCloudConfigurationService,
    eventTemplatingService = eventTemplatingService,
    bitbucketPipelinesService = bitbucketPipelinesService,
) {

    override val type: String = "bitbucket-pipelines"
    override val displayName: String = "Bitbucket Pipelines"

}
