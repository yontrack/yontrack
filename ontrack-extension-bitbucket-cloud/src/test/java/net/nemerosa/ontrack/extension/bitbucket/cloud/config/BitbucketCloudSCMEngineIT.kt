package net.nemerosa.ontrack.extension.bitbucket.cloud.config

import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigMock
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BitbucketCloudProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class BitbucketCloudSCMEngineIT : AbstractDSLTestSupport() {

    /**
     * One name per test instance, so no two tests configure the same project. See #1657.
     */
    private val configuredProjectName = uid("cfg-")

    @Autowired
    private lateinit var bitbucketCloudConfigurationService: BitbucketCloudConfigurationService

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @BeforeEach
    fun cleanup() {
        asAdmin {
            bitbucketCloudConfigurationService.configurations.forEach {
                bitbucketCloudConfigurationService.deleteConfiguration(it.name)
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Jenkins build of a Bitbucket Cloud repository`() {
        val config = bitbucketCloudConfig()
        withDisabledConfigurationTest {
            val build = configTestSupport.configureBuild(
                ci = "jenkins",
                scm = null,
                env = jenkinsBitbucketCloudEnv(),
            )
            assertNotNull(
                propertyService.getPropertyValue(build.project, BitbucketCloudProjectConfigurationPropertyType::class.java),
                "Bitbucket Cloud project configuration has been set"
            ) {
                assertEquals(config.name, it.configuration.name)
                assertEquals("my-workspace", it.workspace)
                assertEquals("my-repository", it.repository)
                assertEquals(0, it.indexationInterval)
                assertEquals(null, it.issueServiceConfigurationIdentifier)
            }
            assertNotNull(
                propertyService.getPropertyValue(build.branch, GitBranchConfigurationPropertyType::class.java),
                "Git branch configuration has been set"
            ) {
                assertEquals(EnvFixtures.TEST_BRANCH, it.branch)
            }
            assertNotNull(
                propertyService.getPropertyValue(build, GitCommitPropertyType::class.java),
                "Git build commit has been set"
            ) {
                assertEquals(EnvFixtures.TEST_COMMIT, it.commit)
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Explicit engine, explicit configuration, indexation interval and issue service`() {
        bitbucketCloudConfig()
        val configName = uid("bbc-")
        val config = bitbucketCloudConfig(name = configName)
        withDisabledConfigurationTest {
            val project = configTestSupport.configureProject(
                yaml = """
                    version: v1
                    configuration:
                      defaults:
                        project:
                          scmConfig: $configName
                          scmIndexationInterval: 30
                          issueServiceIdentifier:
                            serviceId: jira
                            serviceName: JIRA
                """.trimIndent(),
                ci = "jenkins",
                scm = "bitbucket-cloud",
                env = jenkinsBitbucketCloudEnv(gitUrl = "git@bitbucket.org:my-workspace/my-repository.git"),
            )
            assertNotNull(
                propertyService.getPropertyValue(project, BitbucketCloudProjectConfigurationPropertyType::class.java),
                "Bitbucket Cloud project configuration has been set"
            ) {
                assertEquals(config.name, it.configuration.name)
                assertEquals("my-workspace", it.workspace)
                assertEquals("my-repository", it.repository)
                assertEquals(30, it.indexationInterval)
                assertEquals("jira//JIRA", it.issueServiceConfigurationIdentifier)
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Several configurations and no scmConfig`() {
        bitbucketCloudConfig()
        bitbucketCloudConfig()
        withDisabledConfigurationTest {
            assertFailsWith<BitbucketCloudSCMAmbiguousConfigException> {
                configTestSupport.configureProject(
                    ci = "jenkins",
                    scm = null,
                    env = jenkinsBitbucketCloudEnv(),
                )
            }
        }
    }

    @Test
    @AsAdminTest
    fun `No configuration`() {
        withDisabledConfigurationTest {
            assertFailsWith<BitbucketCloudSCMNoConfigException> {
                configTestSupport.configureProject(
                    ci = "jenkins",
                    scm = null,
                    env = jenkinsBitbucketCloudEnv(),
                )
            }
        }
    }

    private fun jenkinsBitbucketCloudEnv(
        gitUrl: String = "https://user@bitbucket.org/my-workspace/my-repository.git",
    ) = EnvFixtures.jenkins(
        extraEnv = mapOf(
            "GIT_URL" to gitUrl,
            "GIT_COMMIT" to EnvFixtures.TEST_COMMIT,
            "PROJECT_NAME" to configuredProjectName,
        )
    )

    private fun bitbucketCloudConfig(
        name: String = uid("bbc-"),
    ): BitbucketCloudConfiguration {
        val configuration = bitbucketCloudTestConfigMock(name = name)
        withDisabledConfigurationTest {
            asAdmin {
                bitbucketCloudConfigurationService.newConfiguration(configuration)
            }
        }
        return configuration
    }

}
