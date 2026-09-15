package net.nemerosa.ontrack.extension.bitbucket.cloud.configuration

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClient
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import net.nemerosa.ontrack.model.support.ConnectionResultType
import org.junit.jupiter.api.Test
import org.springframework.web.client.HttpClientErrorException
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultBitbucketCloudConfigurationServiceTest {

    private val client = mockk<BitbucketCloudClient>()
    private val clientFactory = mockk<BitbucketCloudClientFactory> {
        every { getBitbucketCloudClient(any()) } returns client
    }

    private val service = DefaultBitbucketCloudConfigurationService(
        configurationRepository = mockk(relaxed = true),
        securityService = mockk(relaxed = true),
        encryptionService = mockk(relaxed = true),
        eventPostService = mockk(relaxed = true),
        eventFactory = mockk(relaxed = true),
        ontrackConfigProperties = mockk(relaxed = true),
        bitbucketCloudClientFactory = clientFactory,
    )

    private val config = BitbucketCloudConfiguration(
        name = "bbc",
        authType = BitbucketCloudAuthType.ACCESS_TOKEN,
        token = "token",
    )

    @Test
    fun `Validation OK when the client accepts the credentials`() {
        every { client.validate() } returns Unit
        assertEquals(ConnectionResultType.OK, service.validate(config).type)
    }

    @Test
    fun `Validation error when the client rejects the credentials`() {
        every { client.validate() } throws HttpClientErrorException(HttpStatus.UNAUTHORIZED)
        val result = service.validate(config)
        assertEquals(ConnectionResultType.ERROR, result.type)
        assertTrue(result.message.contains("ACCESS_TOKEN"), result.message)
    }

    @Test
    fun `Validation error when the client cannot be created`() {
        every { clientFactory.getBitbucketCloudClient(any()) } throws IllegalStateException("No token")
        assertEquals(ConnectionResultType.ERROR, service.validate(config).type)
    }
}
