package net.nemerosa.ontrack.extension.bitbucket.cloud.scm

import io.mockk.mockk
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Replaces the Bitbucket Cloud client factory by a mock, stubbed by each test.
 */
class BitbucketCloudClientMockConfig {

    private val clientFactory: BitbucketCloudClientFactory = mockk()

    @Bean
    @Primary
    fun bitbucketCloudClientFactory(): BitbucketCloudClientFactory = clientFactory

}
