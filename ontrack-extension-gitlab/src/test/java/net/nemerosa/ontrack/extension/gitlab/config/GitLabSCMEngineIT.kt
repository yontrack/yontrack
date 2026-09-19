package net.nemerosa.ontrack.extension.gitlab.config

import com.sun.net.httpserver.HttpServer
import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.config.scm.SCMEngineNotDetectedException
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.property.GitLabProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class GitLabSCMEngineIT : AbstractDSLTestSupport() {

    /**
     * One name per test instance, so no two tests configure the same project. See #1657.
     */
    private val configuredProjectName = uid("cfg-")

    @Autowired
    private lateinit var gitLabConfigurationService: GitLabConfigurationService

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @BeforeEach
    fun cleanup() {
        asAdmin {
            gitLabConfigurationService.configurations.forEach {
                gitLabConfigurationService.deleteConfiguration(it.name)
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Jenkins build of a GitLab repository`() {
        // The engine matches on the host, so the instance of this test can be a local stub. It has to be
        // one: setting the Git commit on the build indexes it through the GitLab SCM, and a stub answering
        // 404 is what keeps that lookup - and therefore this test - off the network.
        withGitLabStub { url ->
            val config = gitLabConfig(url = url)
            withDisabledConfigurationTest {
                val build = configTestSupport.configureBuild(
                    ci = "jenkins",
                    scm = null,
                    env = jenkinsGitLabEnv(gitUrl = "$url/nemerosa/yontrack.git"),
                )
                assertNotNull(
                    propertyService.getPropertyValue(build.project, GitLabProjectConfigurationPropertyType::class.java),
                    "GitLab project configuration has been set"
                ) {
                    assertEquals(config.name, it.configuration.name)
                    assertEquals("nemerosa/yontrack", it.repository)
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
    }

    @Test
    @AsAdminTest
    fun `Subgroups are part of the repository path`() {
        gitLabConfig()
        withDisabledConfigurationTest {
            val project = configTestSupport.configureProject(
                ci = "jenkins",
                scm = null,
                env = jenkinsGitLabEnv(gitUrl = "git@gitlab.com:nemerosa/tools/ci/yontrack.git"),
            )
            assertNotNull(
                propertyService.getPropertyValue(project, GitLabProjectConfigurationPropertyType::class.java),
                "GitLab project configuration has been set"
            ) {
                assertEquals("nemerosa/tools/ci/yontrack", it.repository)
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Self-managed instance`() {
        val config = gitLabConfig(url = "https://gitlab.dev.yontrack.com")
        withDisabledConfigurationTest {
            val project = configTestSupport.configureProject(
                ci = "jenkins",
                scm = null,
                env = jenkinsGitLabEnv(gitUrl = "https://gitlab.dev.yontrack.com/nemerosa/yontrack.git"),
            )
            assertNotNull(
                propertyService.getPropertyValue(project, GitLabProjectConfigurationPropertyType::class.java),
                "GitLab project configuration has been set"
            ) {
                assertEquals(config.name, it.configuration.name)
                assertEquals("nemerosa/yontrack", it.repository)
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Explicit engine, explicit configuration, indexation interval and issue service`() {
        gitLabConfig()
        val configName = uid("gl-")
        val config = gitLabConfig(name = configName)
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
                scm = "gitlab",
                env = jenkinsGitLabEnv(),
            )
            assertNotNull(
                propertyService.getPropertyValue(project, GitLabProjectConfigurationPropertyType::class.java),
                "GitLab project configuration has been set"
            ) {
                assertEquals(config.name, it.configuration.name)
                assertEquals("nemerosa/yontrack", it.repository)
                assertEquals(30, it.indexationInterval)
                assertEquals("jira//JIRA", it.issueServiceConfigurationIdentifier)
            }
        }
    }

    @Test
    @AsAdminTest
    fun `Several configurations on the same instance and no scmConfig`() {
        gitLabConfig()
        gitLabConfig()
        withDisabledConfigurationTest {
            assertFailsWith<GitLabSCMAmbiguousConfigException> {
                configTestSupport.configureProject(
                    ci = "jenkins",
                    scm = null,
                    env = jenkinsGitLabEnv(),
                )
            }
        }
    }

    @Test
    @AsAdminTest
    fun `No configuration matching the SCM URL`() {
        gitLabConfig(url = "https://gitlab.dev.yontrack.com")
        withDisabledConfigurationTest {
            assertFailsWith<GitLabSCMNoConfigException> {
                configTestSupport.configureProject(
                    ci = "jenkins",
                    scm = "gitlab",
                    env = jenkinsGitLabEnv(),
                )
            }
        }
    }

    @Test
    @AsAdminTest
    fun `No configuration at all, the engine is not detected`() {
        withDisabledConfigurationTest {
            assertFailsWith<SCMEngineNotDetectedException> {
                configTestSupport.configureProject(
                    ci = "jenkins",
                    scm = null,
                    env = jenkinsGitLabEnv(),
                )
            }
        }
    }

    private fun jenkinsGitLabEnv(
        gitUrl: String = "https://gitlab.com/nemerosa/yontrack.git",
    ) = EnvFixtures.jenkins(
        extraEnv = mapOf(
            "GIT_URL" to gitUrl,
            "GIT_COMMIT" to EnvFixtures.TEST_COMMIT,
            "PROJECT_NAME" to configuredProjectName,
        )
    )

    /**
     * Runs the code against a GitLab instance which answers 404 to everything, on a local port.
     *
     * Nothing of the engine is stubbed by this - it is the commit indexing which runs behind the Git commit
     * property and which would otherwise call the real instance named by the configuration.
     */
    private fun <T> withGitLabStub(code: (url: String) -> T): T {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = """{"message":"404 Not found"}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(404, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        return try {
            code("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }

    private fun gitLabConfig(
        name: String = uid("gl-"),
        url: String = "https://gitlab.com",
    ): GitLabConfiguration {
        val configuration = GitLabConfiguration(
            name = name,
            url = url,
            token = "token",
        )
        withDisabledConfigurationTest {
            asAdmin {
                gitLabConfigurationService.newConfiguration(configuration)
            }
        }
        return configuration
    }
}
