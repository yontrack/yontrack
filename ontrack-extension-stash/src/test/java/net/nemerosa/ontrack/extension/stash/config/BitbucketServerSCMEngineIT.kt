package net.nemerosa.ontrack.extension.stash.config

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.config.model.EnvConstants
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType
import net.nemerosa.ontrack.extension.git.support.GitCommitPropertyCommitLink
import net.nemerosa.ontrack.extension.stash.BitbucketServerFixtures
import net.nemerosa.ontrack.extension.stash.model.StashConfiguration
import net.nemerosa.ontrack.extension.stash.property.StashProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.stash.service.StashConfigurationService
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class BitbucketServerSCMEngineIT : AbstractDSLTestSupport() {

    /**
     * One name per test instance (JUnit builds a new one per method), so no two tests - and no two
     * modules of the same CI shard - configure the same project. See #1657.
     */
    private val configuredProjectName = uid("cfg-")


    @Autowired
    private lateinit var stashConfigurationService: StashConfigurationService

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @BeforeEach
    fun cleanup() {
        stashConfigurationService.configurations.forEach {
            stashConfigurationService.deleteConfiguration(it.name)
        }
    }

    @Test
    @AsAdminTest
    fun `Configuration of the project with unexisting configuration`() {
        assertFailsWith<BitbucketServerSCMUnexistingConfigException> {
            configTestSupport.configureBuild(
                ci = "generic",
                scm = "bitbucket-server",
                env = BitbucketServerSCMEnvFixtures.bitbucketServerEnv(configuredProjectName),
            )
        }
    }

    @Test
    @AsAdminTest
    fun `Configuration of the project with existing configuration being detected`() {
        val config = bitbucketServerConfig()
        val project = configTestSupport.configureProject(
            ci = "generic",
            env = BitbucketServerSCMEnvFixtures.bitbucketServerEnv(configuredProjectName),
        )
        assertNotNull(
            propertyService.getPropertyValue(project, StashProjectConfigurationPropertyType::class.java),
            "Bitbucket Server project configuration has been set"
        ) {
            assertEquals(
                config.name,
                it.configuration.name,
            )
            assertEquals(
                "nemerosa",
                it.project,
            )
            assertEquals(
                "yontrack",
                it.repository,
            )
            assertEquals(
                0,
                it.indexationInterval,
            )
        }
    }

    @Test
    @AsAdminTest
    fun `Configuration of the project with indexation interval`() {
        val config = bitbucketServerConfig()
        val project = configTestSupport.configureProject(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    project:
                      scmIndexationInterval: 30
            """.trimIndent(),
            ci = "generic",
            scm = null,
            env = BitbucketServerSCMEnvFixtures.bitbucketServerEnv(configuredProjectName),
        )
        assertNotNull(
            propertyService.getPropertyValue(project, StashProjectConfigurationPropertyType::class.java),
            "Bitbucket Server project configuration has been set"
        ) {
            assertEquals(
                config.name,
                it.configuration.name,
            )
            assertEquals(
                "nemerosa",
                it.project,
            )
            assertEquals(
                "yontrack",
                it.repository,
            )
            assertEquals(
                30,
                it.indexationInterval,
            )
        }
    }

    @Test
    @AsAdminTest
    fun `Configuration of the project with explicit configuration`() {
        val configName = uid("cfg-")
        val config = bitbucketServerConfig(name = configName)
        val project = configTestSupport.configureProject(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    project:
                      scmConfig: $configName
            """.trimIndent(),
            ci = "generic",
            scm = null,
            env = BitbucketServerSCMEnvFixtures.bitbucketServerEnv(configuredProjectName),
        )
        assertNotNull(
            propertyService.getPropertyValue(project, StashProjectConfigurationPropertyType::class.java),
            "Bitbucket Server project configuration has been set"
        ) {
            assertEquals(
                config.name,
                it.configuration.name,
            )
            assertEquals(
                "nemerosa",
                it.project,
            )
            assertEquals(
                "yontrack",
                it.repository,
            )
        }
    }

    @Test
    @AsAdminTest
    fun `Configuration of the branch with the Git branch`() {
        bitbucketServerConfig()
        val branch = configTestSupport.configureBranch(
            ci = "generic",
            scm = null,
            env = BitbucketServerSCMEnvFixtures.bitbucketServerEnv(configuredProjectName),
        )
        assertNotNull(
            propertyService.getPropertyValue(branch, GitBranchConfigurationPropertyType::class.java),
            "Git branch configuration has been set"
        ) {
            assertEquals(
                EnvFixtures.TEST_BRANCH,
                it.branch,
            )
            assertEquals(
                GitCommitPropertyCommitLink.ID,
                it.buildCommitLink?.id,
            )
            assertEquals(
                false,
                it.override,
            )
            assertEquals(
                0,
                it.buildTagInterval,
            )
        }
    }

    @Test
    @AsAdminTest
    fun `Configuration of the build with the Git commit`() {
        bitbucketServerConfig()
        withDisabledConfigurationTest {
            val build = configTestSupport.configureBuild(
                ci = "generic",
                scm = null,
                env = BitbucketServerSCMEnvFixtures.bitbucketServerEnv(configuredProjectName),
            )
            assertNotNull(
                propertyService.getPropertyValue(build, GitCommitPropertyType::class.java),
                "Git build commit has been set"
            ) {
                assertEquals(
                    EnvFixtures.TEST_COMMIT,
                    it.commit,
                )
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Configuration of the project with an explicit issue service identifier`() {
        bitbucketServerConfig()
        withDisabledConfigurationTest {
            val project = configTestSupport.configureProject(
                yaml = """
                    version: v1
                    configuration:
                        defaults:
                            project:
                                issueServiceIdentifier:
                                    serviceId: jira
                                    serviceName: JIRA
                """.trimIndent(),
                ci = "generic",
                scm = null,
                env = BitbucketServerSCMEnvFixtures.bitbucketServerEnv(configuredProjectName),
            )
            assertNotNull(
                propertyService.getPropertyValue(project, StashProjectConfigurationPropertyType::class.java),
                "GitHub project config has been set"
            ) {
                assertEquals(
                    "jira//JIRA",
                    it.issueServiceConfigurationIdentifier
                )
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Configuration of the project with a default issue service identifier from the legacy environment`() {
        bitbucketServerConfig()
        withDisabledConfigurationTest {
            val project = configTestSupport.configureProject(
                yaml = """
                    version: v1
                    configuration: {}
                """.trimIndent(),
                ci = "generic",
                scm = null,
                env = BitbucketServerSCMEnvFixtures.bitbucketServerEnv(
                    configuredProjectName,
                    extraEnv = mapOf(
                        EnvConstants.YONTRACK_LEGACY_SCM_ISSUES to "jira//JIRA",
                    )
                ),
            )
            assertNotNull(
                propertyService.getPropertyValue(project, StashProjectConfigurationPropertyType::class.java),
                "GitHub project config has been set"
            ) {
                assertEquals(
                    "jira//JIRA",
                    it.issueServiceConfigurationIdentifier
                )
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Configuration of the project with a default issue service identifier from the environment`() {
        bitbucketServerConfig()
        withDisabledConfigurationTest {
            val project = configTestSupport.configureProject(
                yaml = """
                    version: v1
                    configuration: {}
                """.trimIndent(),
                ci = "generic",
                scm = null,
                env = BitbucketServerSCMEnvFixtures.bitbucketServerEnv(
                    configuredProjectName,
                    extraEnv = mapOf(
                        EnvConstants.YONTRACK_CI_SCM_ISSUES to "jira//JIRA",
                    )
                ),
            )
            assertNotNull(
                propertyService.getPropertyValue(project, StashProjectConfigurationPropertyType::class.java),
                "GitHub project config has been set"
            ) {
                assertEquals(
                    "jira//JIRA",
                    it.issueServiceConfigurationIdentifier
                )
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Configuration of the project with an explicit issue service identifier overriding the environment`() {
        bitbucketServerConfig()
        withDisabledConfigurationTest {
            val project = configTestSupport.configureProject(
                yaml = """
                    version: v1
                    configuration:
                        defaults:
                            project:
                                issueServiceIdentifier:
                                    serviceId: jira
                                    serviceName: JIRA
                """.trimIndent(),
                ci = "generic",
                scm = null,
                env = BitbucketServerSCMEnvFixtures.bitbucketServerEnv(
                    configuredProjectName,
                    extraEnv = mapOf(
                        EnvConstants.YONTRACK_CI_SCM_ISSUES to "jira//OTHER",
                    )
                ),
            )
            assertNotNull(
                propertyService.getPropertyValue(project, StashProjectConfigurationPropertyType::class.java),
                "GitHub project config has been set"
            ) {
                assertEquals(
                    "jira//JIRA",
                    it.issueServiceConfigurationIdentifier
                )
            }
        }
    }

    private fun bitbucketServerConfig(
        name: String = uid("stash-"),
        url: String = BitbucketServerFixtures.BITBUCKET_SERVER_URL,
    ): StashConfiguration {
        val configuration = BitbucketServerFixtures.bitbucketServerConfig(
            name = name,
            url = url,
        )
        withDisabledConfigurationTest {
            stashConfigurationService.newConfiguration(
                configuration,
            )
        }
        return configuration
    }

}