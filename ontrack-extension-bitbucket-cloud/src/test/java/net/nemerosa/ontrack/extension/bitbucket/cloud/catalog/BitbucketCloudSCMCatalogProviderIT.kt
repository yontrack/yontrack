package net.nemerosa.ontrack.extension.bitbucket.cloud.catalog

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.bitbucket.cloud.*
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BitbucketCloudProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalogEntry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.*

class BitbucketCloudSCMCatalogProviderIT : AbstractBitbucketCloudTestSupport() {

    @Autowired
    private lateinit var bitbucketCloudSCMCatalogProvider: BitbucketCloudSCMCatalogProvider

    @Test
    fun `Catalog provider id`() {
        assertEquals("bitbucket-cloud", bitbucketCloudSCMCatalogProvider.id)
    }

    @Test
    fun `No entries when no project uses the configuration`() {
        deleteAllConfigs()
        createMockConfig()
        assertTrue(bitbucketCloudSCMCatalogProvider.entries.isEmpty())
    }

    @TestOnBitbucketCloud
    fun `Getting the list of entries with an API token`() {
        checkEntries(bitbucketCloudTestConfigReal())
    }

    @TestOnBitbucketCloud
    fun `Getting the list of entries with an access token`() {
        checkEntries(bitbucketCloudTestConfigRealAccessToken())
    }

    private fun checkEntries(config: BitbucketCloudConfiguration) {
        deleteAllConfigs()
        val workspace = bitbucketCloudTestEnv.workspace
        val expectedRepository = bitbucketCloudTestEnv.repository
        asAdmin {
            bitbucketCloudConfigurationService.newConfiguration(config)
            // The workspace is known through a project property
            project {
                setBitbucketCloudProperty(config, expectedRepository, workspace = workspace)
            }
        }
        // Collects the SCM catalog entries
        val entries = bitbucketCloudSCMCatalogProvider.entries
        val entry = entries.find { it.repository == "$workspace/$expectedRepository" }
        assertNotNull(entry, "Expected SCM source") { source ->
            assertEquals(config.name, source.config)
            assertEquals("https://bitbucket.org/$workspace/$expectedRepository", source.repositoryPage)
            assertNotNull(source.lastActivity, "Last activity is set")
            assertNull(source.teams, "Bitbucket Cloud teams are not supported yet")
        }
    }

    @Test
    fun `BBC SCM catalog provider does not match a project not configured for BBC`() {
        asAdmin {
            val config = createMockConfig()
            val entry = scmCatalogEntry(config)
            project {
                assertFalse(bitbucketCloudSCMCatalogProvider.matches(entry, this))
            }
        }
    }

    @Test
    fun `BBC SCM catalog provider does not match a project configured for another BBC configuration`() {
        asAdmin {
            val entryConfig = createMockConfig()
            val projectConfig = createMockConfig()
            val entry = scmCatalogEntry(entryConfig)
            project {
                setBitbucketCloudProperty(projectConfig, "my-repository")
                assertFalse(bitbucketCloudSCMCatalogProvider.matches(entry, this))
            }
        }
    }

    @Test
    fun `BBC SCM catalog provider does not match a project configured for another workspace`() {
        asAdmin {
            val config = createMockConfig()
            val entry = scmCatalogEntry(config)
            project {
                setBitbucketCloudProperty(config, "my-repository", workspace = "another-workspace")
                assertFalse(bitbucketCloudSCMCatalogProvider.matches(entry, this))
            }
        }
    }

    @Test
    fun `BBC SCM catalog provider matches a project configured for the same BBC configuration`() {
        asAdmin {
            val config = createMockConfig()
            val entry = scmCatalogEntry(config)
            project {
                setBitbucketCloudProperty(config, "my-repository")
                assertTrue(bitbucketCloudSCMCatalogProvider.matches(entry, this))
            }
        }
    }

    @Test
    fun `Linking a project to a catalog entry sets the workspace and the repository`() {
        asAdmin {
            val config = createMockConfig()
            val entry = scmCatalogEntry(config)
            project {
                assertTrue(bitbucketCloudSCMCatalogProvider.linkProjectToSCM(this, entry))
                assertNotNull(
                    propertyService.getProperty(this, BitbucketCloudProjectConfigurationPropertyType::class.java).value
                ) {
                    assertEquals(config.name, it.configuration.name)
                    assertEquals("my-workspace", it.workspace)
                    assertEquals("my-repository", it.repository)
                }
            }
        }
    }

    private fun createMockConfig(): BitbucketCloudConfiguration {
        val config = bitbucketCloudTestConfigMock()
        withDisabledConfigurationTest {
            asAdmin {
                bitbucketCloudConfigurationService.newConfiguration(config)
            }
        }
        return config
    }

    private fun scmCatalogEntry(config: BitbucketCloudConfiguration) =
        SCMCatalogEntry(
            scm = "bitbucket-cloud",
            config = config.name,
            repository = "my-workspace/my-repository",
            repositoryPage = "",
            lastActivity = null,
            createdAt = null,
            timestamp = Time.now(),
            teams = null,
        )

}
