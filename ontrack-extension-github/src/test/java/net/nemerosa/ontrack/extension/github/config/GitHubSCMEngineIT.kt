package net.nemerosa.ontrack.extension.github.config

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.config.model.EnvConstants
import net.nemerosa.ontrack.extension.config.scm.SCMEngineNotDetectedException
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType
import net.nemerosa.ontrack.extension.git.support.GitCommitPropertyCommitLink
import net.nemerosa.ontrack.extension.github.AbstractGitHubTestSupport
import net.nemerosa.ontrack.extension.github.client.UseGitHubClientMock
import net.nemerosa.ontrack.extension.github.property.GitHubProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.it.deprecatedUsages
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@UseGitHubClientMock
class GitHubSCMEngineIT : AbstractGitHubTestSupport() {

    @Autowired
    private lateinit var gitHubConfigurationService: GitHubConfigurationService

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @BeforeEach
    fun cleanup() {
        gitHubConfigurationService.configurations.forEach {
            gitHubConfigurationService.deleteConfiguration(it.name)
        }
    }

    @Test
    @AsAdminTest
    fun `Configuration of the project with unexisting configuration`() {
        gitHubConfiguration(url = "https://github.dev.yontrack.com")
        assertFailsWith<SCMEngineNotDetectedException> {
            configTestSupport.configureBuild(
                env = EnvFixtures.gitHub(),
            )
        }
    }

    @Test
    @AsAdminTest
    fun `Configuration of the project with existing configuration being detected`() {
        val config = gitHubConfiguration()
        val project = configTestSupport.configureProject(
            ci = null,
            scm = null,
            env = EnvFixtures.gitHub(),
        )
        assertNotNull(
            propertyService.getPropertyValue(project, GitHubProjectConfigurationPropertyType::class.java),
            "GitHub project configuration has been set"
        ) {
            assertEquals(
                config.name,
                it.configuration.name,
            )
            assertEquals(
                "yontrack/yontrack",
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
        val config = gitHubConfiguration()
        val project = configTestSupport.configureProject(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    project:
                      scmIndexationInterval: 30
            """.trimIndent(),
            ci = null,
            scm = null,
            env = EnvFixtures.gitHub(),
        )
        assertNotNull(
            propertyService.getPropertyValue(project, GitHubProjectConfigurationPropertyType::class.java),
            "GitHub project configuration has been set"
        ) {
            assertEquals(
                config.name,
                it.configuration.name,
            )
            assertEquals(
                "yontrack/yontrack",
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
        val config = gitHubConfiguration(gitConfigurationName = configName)
        val project = configTestSupport.configureProject(
            yaml = """
                version: v1
                configuration:
                  defaults:
                    project:
                      scmConfig: $configName
            """.trimIndent(),
            ci = null,
            scm = null,
            env = EnvFixtures.gitHub(),
        )
        assertNotNull(
            propertyService.getPropertyValue(project, GitHubProjectConfigurationPropertyType::class.java),
            "GitHub project configuration has been set"
        ) {
            assertEquals(
                config.name,
                it.configuration.name,
            )
            assertEquals(
                "yontrack/yontrack",
                it.repository,
            )
        }
    }

    @Test
    @AsAdminTest
    fun `Configuration of the branch with the Git branch`() {
        gitHubConfiguration()
        val branch = configTestSupport.configureBranch(
            ci = null,
            scm = null,
            env = EnvFixtures.gitHub(),
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
        gitHubConfiguration()
        withDisabledConfigurationTest {
            val build = configTestSupport.configureBuild(
                ci = null,
                scm = null,
                env = EnvFixtures.gitHub(),
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
        gitHubConfiguration()
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
                ci = null,
                scm = null,
                env = EnvFixtures.gitHub(),
            )
            assertNotNull(
                propertyService.getPropertyValue(project, GitHubProjectConfigurationPropertyType::class.java),
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
        gitHubConfiguration()
        withDisabledConfigurationTest {
            lateinit var project: Project
            val usages = meterRegistry.deprecatedUsages(DeprecationSurface.ENV, "ONTRACK_SCM_ISSUES") {
                project = configTestSupport.configureProject(
                    yaml = """
                        version: v1
                        configuration: {}
                    """.trimIndent(),
                    ci = null,
                    scm = null,
                    env = EnvFixtures.gitHub(
                        extraEnv = mapOf(
                            "ONTRACK_SCM_ISSUES" to "jira//JIRA",
                        )
                    ),
                )
            }
            assertEquals(1.0, usages, "The legacy environment variable is reported as deprecated")
            assertNotNull(
                propertyService.getPropertyValue(project, GitHubProjectConfigurationPropertyType::class.java),
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
        gitHubConfiguration()
        withDisabledConfigurationTest {
            lateinit var project: Project
            val usages = meterRegistry.deprecatedUsages(DeprecationSurface.ENV, "ONTRACK_SCM_ISSUES") {
                project = configTestSupport.configureProject(
                    yaml = """
                        version: v1
                        configuration: {}
                    """.trimIndent(),
                    ci = null,
                    scm = null,
                    env = EnvFixtures.gitHub(
                        extraEnv = mapOf(
                            EnvConstants.YONTRACK_CI_SCM_ISSUES to "jira//JIRA",
                        )
                    ),
                )
            }
            assertEquals(0.0, usages, "The environment variable is not deprecated")
            assertNotNull(
                propertyService.getPropertyValue(project, GitHubProjectConfigurationPropertyType::class.java),
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
        gitHubConfiguration()
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
                ci = null,
                scm = null,
                env = EnvFixtures.gitHub(
                    extraEnv = mapOf(
                        EnvConstants.YONTRACK_CI_SCM_ISSUES to "jira//OTHER",
                    )
                ),
            )
            assertNotNull(
                propertyService.getPropertyValue(project, GitHubProjectConfigurationPropertyType::class.java),
                "GitHub project config has been set"
            ) {
                assertEquals(
                    "jira//JIRA",
                    it.issueServiceConfigurationIdentifier
                )
            }
        }
    }

}