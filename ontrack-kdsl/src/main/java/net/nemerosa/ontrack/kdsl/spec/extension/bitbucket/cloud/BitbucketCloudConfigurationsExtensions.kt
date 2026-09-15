package net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud

import net.nemerosa.ontrack.kdsl.spec.configurations.ConfigurationInterface
import net.nemerosa.ontrack.kdsl.spec.configurations.ConfigurationsMgt

/**
 * Management of the Bitbucket Cloud configurations (create, find, delete).
 */
val ConfigurationsMgt.bitbucketCloud: ConfigurationInterface<BitbucketCloudConfiguration>
    get() = ConfigurationInterface(
        connector = connector,
        id = "bitbucket-cloud",
        type = BitbucketCloudConfiguration::class,
    )
