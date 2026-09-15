package net.nemerosa.ontrack.extension.bitbucket.cloud.client

import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import org.springframework.stereotype.Component

@Component
class DefaultBitbucketCloudClientFactory : BitbucketCloudClientFactory {

    override fun getBitbucketCloudClient(config: BitbucketCloudConfiguration): BitbucketCloudClient =
        DefaultBitbucketCloudClient(config)

}
