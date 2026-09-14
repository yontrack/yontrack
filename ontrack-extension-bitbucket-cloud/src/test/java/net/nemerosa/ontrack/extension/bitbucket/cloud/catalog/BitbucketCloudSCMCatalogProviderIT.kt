package net.nemerosa.ontrack.extension.bitbucket.cloud.catalog

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.bitbucket.cloud.*
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalogEntry
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.*

class BitbucketCloudSCMCatalogProviderIT : AbstractBitbucketCloudTestSupport() {

    @Autowired
    private lateinit var bitbucketCloudSCMCatalogProvider: BitbucketCloudSCMCatalogProvider

    @TestOnBitbucketCloud
    fun `Getting the list of entries`() {
        // Deleting all configurations
        deleteAllConfigs()
        // Creates a configuration
        val config = bitbucketCloudTestConfigReal()
        asAdmin {
            bitbucketCloudConfigurationService.newConfiguration(config)
        }
        // Collects the SCM catalog entries
        val entries = bitbucketCloudSCMCatalogProvider.entries
        val expectedRepository = bitbucketCloudTestEnv.repository
        val entry = entries.find { it.repository == "${config.workspace}/$expectedRepository" }
        assertNotNull(entry, "Expected SCM source") { source ->
            assertEquals(config.name, source.config)
            assertEquals("https://bitbucket.org/${config.workspace}/$expectedRepository", source.repositoryPage)
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
                setBitbucketCloudProperty(projectConfig, "another-repository")
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

    private fun createMockConfig(): BitbucketCloudConfiguration {
        val config = bitbucketCloudTestConfigMock()
        withDisabledConfigurationTest {
            bitbucketCloudConfigurationService.newConfiguration(config)
        }
        return config
    }

    private fun scmCatalogEntry(config: BitbucketCloudConfiguration) =
        SCMCatalogEntry(
            scm = "bitbucket-cloud",
            config = config.name,
            repository = "${config.workspace}/my-repository",
            repositoryPage = "",
            lastActivity = null,
            createdAt = null,
            timestamp = Time.now(),
            teams = null,
        )

}