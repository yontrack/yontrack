package net.nemerosa.ontrack.extension.bitbucket.cloud.client

import net.nemerosa.ontrack.extension.bitbucket.cloud.TestOnBitbucketCloud
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigReal
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigRealAccessToken
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestEnv
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DefaultBitbucketCloudClientIT {

    private val apiTokenClient get() = DefaultBitbucketCloudClient(bitbucketCloudTestConfigReal())
    private val accessTokenClient get() = DefaultBitbucketCloudClient(bitbucketCloudTestConfigRealAccessToken())

    @TestOnBitbucketCloud
    fun `Validation with an API token`() {
        apiTokenClient.validate()
    }

    @TestOnBitbucketCloud
    fun `Validation with an access token`() {
        accessTokenClient.validate()
    }

    @TestOnBitbucketCloud
    fun `Validation with an invalid access token`() {
        assertThrows<Exception> {
            DefaultBitbucketCloudClient(
                bitbucketCloudTestConfigRealAccessToken().copy(token = "invalid")
            ).validate()
        }
    }

    @TestOnBitbucketCloud
    fun `Validation with an access token used as an API token`() {
        assertThrows<Exception> {
            DefaultBitbucketCloudClient(
                bitbucketCloudTestConfigReal().copy(
                    authType = BitbucketCloudAuthType.API_TOKEN,
                    token = bitbucketCloudTestEnv.accessToken,
                )
            ).validate()
        }
    }

    @TestOnBitbucketCloud
    fun `Getting the list of repositories with an API token`() {
        checkRepositories(apiTokenClient)
    }

    @TestOnBitbucketCloud
    fun `Getting the list of repositories with an access token`() {
        checkRepositories(accessTokenClient)
    }

    @TestOnBitbucketCloud
    fun `Getting a repository with an access token`() {
        val env = bitbucketCloudTestEnv
        val repository = accessTokenClient.getRepository(env.workspace, env.repository)
        assertEquals(env.project, repository.project.key)
    }

    private fun checkRepositories(client: BitbucketCloudClient) {
        val env = bitbucketCloudTestEnv
        val repository = client.getRepositories(env.workspace).find { it.slug == env.repository }
        assertNotNull(repository, "Expected repository has been found") {
            assertNotNull(client.getRepositoryLastModified(it), "Last modified date is set")
            assertEquals(env.project, it.project.key, "Project is read")
        }
    }

}
