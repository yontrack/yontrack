package net.nemerosa.ontrack.kdsl.acceptance.tests.bitbucket.cloud

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.spec.configurations.configurations
import net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud.BitbucketCloudConfiguration
import net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud.BitbucketCloudProjectConfigurationProperty
import net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud.bitbucketCloud
import net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud.bitbucketCloudConfigurationProperty
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Acceptance tests for the Bitbucket Cloud configurations and project property.
 */
class ACCBitbucketCloudExtension : AbstractACCDSLTestSupport() {

    @Test
    fun `Creation, obfuscation and deletion of an API token configuration`() {
        val confName = uid("bbc_")
        ontrack.configurations.bitbucketCloud.create(
            BitbucketCloudConfiguration(
                name = confName,
                authType = BitbucketCloudConfiguration.API_TOKEN,
                email = "bot@example.com",
                token = "secret",
                autoMergeEmail = "approver@example.com",
                autoMergeToken = "approver-secret",
            )
        )
        val conf = ontrack.configurations.bitbucketCloud.findByName(confName)
            ?: fail("Could not find the Bitbucket Cloud configuration")
        assertEquals(confName, conf.name)
        assertEquals(BitbucketCloudConfiguration.API_TOKEN, conf.authType)
        assertEquals("bot@example.com", conf.email)
        assertTrue(conf.token.isNullOrBlank(), "Token is not exposed")
        assertEquals("approver@example.com", conf.autoMergeEmail)
        assertTrue(conf.autoMergeToken.isNullOrBlank(), "Auto merge token is not exposed")

        ontrack.configurations.bitbucketCloud.delete(confName)
        assertNull(ontrack.configurations.bitbucketCloud.findByName(confName), "Configuration has been deleted")
    }

    @Test
    fun `Creation and obfuscation of an access token configuration`() {
        val confName = uid("bbc_")
        ontrack.configurations.bitbucketCloud.create(
            BitbucketCloudConfiguration(
                name = confName,
                authType = BitbucketCloudConfiguration.ACCESS_TOKEN,
                token = "secret",
            )
        )
        val conf = ontrack.configurations.bitbucketCloud.findByName(confName)
            ?: fail("Could not find the Bitbucket Cloud configuration")
        assertEquals(BitbucketCloudConfiguration.ACCESS_TOKEN, conf.authType)
        assertNull(conf.email)
        assertTrue(conf.token.isNullOrBlank(), "Token is not exposed")
    }

    @Test
    fun `Setting the Bitbucket Cloud property on a project`() {
        val confName = uid("bbc_")
        ontrack.configurations.bitbucketCloud.create(
            BitbucketCloudConfiguration(
                name = confName,
                authType = BitbucketCloudConfiguration.ACCESS_TOKEN,
                token = "secret",
            )
        )
        project {
            bitbucketCloudConfigurationProperty = BitbucketCloudProjectConfigurationProperty(
                configuration = confName,
                workspace = "my-workspace",
                repository = "my-repository",
                indexationInterval = 30,
            )
            assertNotNull(bitbucketCloudConfigurationProperty, "Property is set") {
                assertEquals(confName, it.configuration)
                assertEquals("my-workspace", it.workspace)
                assertEquals("my-repository", it.repository)
                assertEquals(30, it.indexationInterval)
                assertNull(it.issueServiceConfigurationIdentifier)
            }

            // Deleting the configuration removes the property
            ontrack.configurations.bitbucketCloud.delete(confName)
            assertNull(bitbucketCloudConfigurationProperty, "Property is gone with its configuration")
        }
    }
}
