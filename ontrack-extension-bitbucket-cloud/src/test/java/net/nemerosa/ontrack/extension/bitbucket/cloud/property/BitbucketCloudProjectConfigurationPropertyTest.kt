package net.nemerosa.ontrack.extension.bitbucket.cloud.property

import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigMock
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.git.UsernamePasswordGitRepositoryAuthenticator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BitbucketCloudProjectConfigurationPropertyTest {

    private fun property(authType: BitbucketCloudAuthType = BitbucketCloudAuthType.API_TOKEN) =
        BitbucketCloudProjectConfigurationProperty(
            configuration = bitbucketCloudTestConfigMock(authType = authType),
            workspace = "my-workspace",
            repository = "my-repository",
            indexationInterval = 0,
            issueServiceConfigurationIdentifier = null,
        )

    @Test
    fun `Repository URL uses the workspace of the property`() {
        assertEquals("https://bitbucket.org/my-workspace/my-repository", property().repositoryUrl)
        assertEquals("my-workspace/my-repository", property().fullName)
    }

    @Test
    fun `Git configuration links`() {
        val git = BitbucketCloudGitConfiguration(property(), null)
        assertEquals("https://bitbucket.org/my-workspace/my-repository.git", git.remote)
        assertEquals("https://bitbucket.org/my-workspace/my-repository/commits/{commit}", git.commitLink)
        assertEquals("https://bitbucket.org/my-workspace/my-repository/src/{commit}/{path}", git.fileAtCommitLink)
    }

    @Test
    fun `Git authentication with an API token`() {
        val git = BitbucketCloudGitConfiguration(property(BitbucketCloudAuthType.API_TOKEN), null)
        assertEquals(
            UsernamePasswordGitRepositoryAuthenticator("x-bitbucket-api-token-auth", "token"),
            git.authenticator
        )
    }

    @Test
    fun `Git authentication with an access token`() {
        val git = BitbucketCloudGitConfiguration(property(BitbucketCloudAuthType.ACCESS_TOKEN), null)
        assertEquals(
            UsernamePasswordGitRepositoryAuthenticator("x-token-auth", "token"),
            git.authenticator
        )
    }

}
