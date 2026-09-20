package net.nemerosa.ontrack.extension.gitlab.config

import com.sun.net.httpserver.HttpServer
import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.ci.CIConfigPRNotSupportedException
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.property.BuildGitLabPipelineRunProperty
import net.nemerosa.ontrack.extension.gitlab.property.BuildGitLabPipelineRunPropertyType
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitLabCIEngineIT : AbstractDSLTestSupport() {

    /**
     * One project path per test instance, so no two tests configure the same project. See #1657.
     */
    private val projectName = uid("proj-")

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
    fun `GitLab CI build detected without any ci or scm`() {
        // The engine matches on the host, so the instance of this test can be a local stub. It has to be
        // one: setting the Git commit on the build indexes it through the GitLab SCM, and a stub answering
        // 404 is what keeps that lookup - and therefore this test - off the network.
        withGitLabStub { url ->
            val config = gitLabConfig(url = url)
            withDisabledConfigurationTest {
                val build = configTestSupport.configureBuild(
                    ci = null,
                    scm = null,
                    env = gitLabCIEnv(url),
                )
                assertEquals(projectName, build.project.name)
                assertEquals("release-1.0", build.branch.name)
                assertTrue(build.name.endsWith("-42"), "Build suffix is the pipeline IID")
                assertNotNull(
                    propertyService.getPropertyValue(
                        build.project,
                        GitLabProjectConfigurationPropertyType::class.java
                    ),
                    "GitLab project configuration has been set"
                ) {
                    assertEquals(config.name, it.configuration.name)
                    assertEquals("nemerosa/$projectName", it.repository)
                }
                assertNotNull(
                    propertyService.getPropertyValue(build.branch, GitBranchConfigurationPropertyType::class.java),
                    "Git branch configuration has been set"
                ) {
                    assertEquals("release/1.0", it.branch)
                }
                assertNotNull(
                    propertyService.getPropertyValue(build, GitCommitPropertyType::class.java),
                    "Git build commit has been set"
                ) {
                    assertEquals(COMMIT, it.commit)
                }
                assertNotNull(
                    propertyService.getPropertyValue(build, BuildGitLabPipelineRunPropertyType::class.java),
                    "GitLab pipeline run has been set"
                ) {
                    assertEquals(
                        BuildGitLabPipelineRunProperty(
                            projectPath = "nemerosa/$projectName",
                            pipelineId = 1234567L,
                            pipelineIid = 42,
                            url = "$url/nemerosa/$projectName/-/pipelines/1234567",
                        ),
                        it
                    )
                }
            }
        }
    }

    /**
     * The whole point of the `CI_REPOSITORY_URL` rule: the job token it carries must not be stored anywhere.
     */
    @Test
    @AsAdminTest
    fun `The job token of CI_REPOSITORY_URL is not stored in any property`() {
        withGitLabStub { url ->
            gitLabConfig(url = url)
            withDisabledConfigurationTest {
                val build = configTestSupport.configureBuild(
                    ci = null,
                    scm = null,
                    env = gitLabCIEnv(url),
                )
                val stored = listOf(
                    propertyService.getPropertyValue(
                        build.project,
                        GitLabProjectConfigurationPropertyType::class.java
                    )?.repository,
                    propertyService.getPropertyValue(
                        build,
                        BuildGitLabPipelineRunPropertyType::class.java
                    )?.let { "${it.projectPath} ${it.url}" },
                ).joinToString(" ")
                assertFalse(stored.contains(JOB_TOKEN), "No job token stored in the properties")
                assertFalse(stored.contains("gitlab-ci-token"), "No job token user stored in the properties")
            }
        }
    }

    @Test
    @AsAdminTest
    fun `GitLab CI merge request pipeline is rejected`() {
        withGitLabStub { url ->
            gitLabConfig(url = url)
            withDisabledConfigurationTest {
                assertFailsWith<CIConfigPRNotSupportedException> {
                    configTestSupport.configureBuild(
                        ci = null,
                        scm = null,
                        env = gitLabCIEnv(url) + ("CI_MERGE_REQUEST_IID" to "12"),
                    )
                }
            }
        }
    }

    private fun gitLabCIEnv(url: String) = mapOf(
        "GITLAB_CI" to "true",
        "CI_PROJECT_URL" to "$url/nemerosa/$projectName",
        "CI_PROJECT_PATH" to "nemerosa/$projectName",
        "CI_PROJECT_NAME" to projectName,
        "CI_COMMIT_SHA" to COMMIT,
        "CI_COMMIT_REF_NAME" to "release/1.0",
        "CI_PIPELINE_ID" to "1234567",
        "CI_PIPELINE_IID" to "42",
        "CI_PIPELINE_URL" to "$url/nemerosa/$projectName/-/pipelines/1234567",
        // Never to be used: it carries the job token.
        "CI_REPOSITORY_URL" to "https://gitlab-ci-token:$JOB_TOKEN@gitlab.com/nemerosa/$projectName.git",
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
        url: String,
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

    companion object {
        private const val COMMIT = "7c0b1745f513b9162791651582c0044d7b6d2a83"
        private const val JOB_TOKEN = "glcbt-secret-job-token"
    }
}
