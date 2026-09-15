package net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud

import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.spec.Ontrack

/**
 * Bitbucket Cloud management.
 */
class BitbucketCloudMgt(connector: Connector) : Connected(connector)

val Ontrack.bitbucketCloud: BitbucketCloudMgt get() = BitbucketCloudMgt(connector)
