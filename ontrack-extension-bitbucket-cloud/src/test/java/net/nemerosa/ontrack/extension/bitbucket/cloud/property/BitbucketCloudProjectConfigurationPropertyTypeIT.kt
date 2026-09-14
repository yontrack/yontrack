package net.nemerosa.ontrack.extension.bitbucket.cloud.property

import net.nemerosa.ontrack.extension.bitbucket.cloud.*
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BitbucketCloudProjectConfigurationPropertyTypeIT : AbstractBitbucketCloudTestSupport() {

    @Test
    fun `Setting the property on a project`() {
        withDisabledConfigurationTest {
            asAdmin {
                project {
                    val config = bitbucketCloudTestConfigMock()
                    bitbucketCloudConfigurationService.newConfiguration(config)
                    setBitbucketCloudProperty(
                        config,
                        repository = "my-repository",
                        indexationInterval = 30,
                        issueServiceConfigurationIdentifier = "jira//my-jira",
                    )
                    // Gets the property back
                    assertNotNull(
                        propertyService.getProperty(
                            this,
                            BitbucketCloudProjectConfigurationPropertyType::class.java
                        ).value
                    ) {
                        assertEquals(config.name, it.configuration.name)
                        assertEquals("my-repository", it.repository)
                        assertEquals(30, it.indexationInterval)
                        assertEquals("jira//my-jira", it.issueServiceConfigurationIdentifier)
                    }
                }
            }
        }
    }

    @Test
    fun `Cleanup of the project property when configuration is deleted`() {
        withDisabledConfigurationTest {
            asAdmin {
                project {
                    val config = withBitbucketCloudProperty()
                    // Checks the property is set
                    assertTrue(propertyService.hasProperty(this, BitbucketCloudProjectConfigurationPropertyType::class.java), "Property is set")
                    // Deletes the configuration
                    bitbucketCloudConfigurationService.deleteConfiguration(config.name)
                    // Checks the property's gone
                    assertFalse(propertyService.hasProperty(this, BitbucketCloudProjectConfigurationPropertyType::class.java), "Property is unset")
                }
            }
        }
    }

    @TestOnBitbucketCloud
    fun `Project information in the property decorations`() {
        val expectedRepository = bitbucketCloudTestEnv.repository
        val expectedProject = bitbucketCloudTestEnv.project
        asAdmin {
            project {
                val config = bitbucketCloudTestConfigReal()
                bitbucketCloudConfigurationService.newConfiguration(config)
                setBitbucketCloudProperty(config, expectedRepository, 0, null)
                // Gets the property
                assertNotNull(propertyService.getProperty(this, BitbucketCloudProjectConfigurationPropertyType::class.java).value) {
                    // Gets its decorations
                    val decorations = bitbucketCloudProjectConfigurationPropertyType.getPropertyDecorations(it)
                    // Checks that they contain the project information
                    assertNotNull(decorations["projectInfo"]) { node ->
                        assertIs<BitbucketCloudProjectConfigurationPropertyType.BitbucketCloudProjectProperty>(node) { info ->
                            assertEquals(expectedProject, info.project.key)
                            assertEquals("https://bitbucket.org/${config.workspace}/workspace/projects/$expectedProject", info.url)
                        }
                    }
                }
            }
        }
    }

}