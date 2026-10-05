package net.nemerosa.ontrack.extension.github.config

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.github.AbstractGitHubTestSupport
import net.nemerosa.ontrack.extension.github.client.UseGitHubClientMock
import net.nemerosa.ontrack.extension.github.workflow.BuildGitHubWorkflowRunPropertyType
import net.nemerosa.ontrack.it.AsAdminTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@UseGitHubClientMock
class GitHubCIEngineIT : AbstractGitHubTestSupport() {

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Test
    @AsAdminTest
    fun `GitHub CI engine generating the project name from the Git URL`() {
        configTestSupport.withConfigAndProject(
            """
                version: v1
                configuration: {}
            """.trimIndent(),
            ci = null,
            env = EnvFixtures.gitHub(
                projectName = "ontrack",
            ),
            expectedProjectName = "ontrack",
        ) { project, _ ->
            assertEquals("ontrack", project.name)
        }
    }

    @Test
    @AsAdminTest
    fun `GitHub CI engine generating the project name from an environment variable`() {
        configTestSupport.withConfigAndProject(
            """
                version: v1
                configuration: {}
            """.trimIndent(),
            ci = null,
            env = EnvFixtures.gitHub(
                extraEnv = mapOf("PROJECT_NAME" to "yontrack"),
            ),
            expectedProjectName = "yontrack",
        ) { project, _ ->
            assertEquals("yontrack", project.name)
        }
    }

    @Test
    @AsAdminTest
    fun `GitHub CI engine generating the project name from the configuration`() {
        configTestSupport.withConfigAndProject(
            """
                version: v1
                configuration:
                  defaults:
                    project:
                      name: "nemerosa"
            """.trimIndent(),
            ci = null,
            env = EnvFixtures.gitHub(
                extraEnv = mapOf(
                    "PROJECT_NAME" to "yontrack",
                )
            ),
            expectedProjectName = "nemerosa"
        ) { project, _ ->
            assertEquals("nemerosa", project.name)
        }
    }

    @Test
    @AsAdminTest
    fun `GitHub CI engine getting the branch name from the native environment`() {
        gitHubConfiguration()
        val branch = configTestSupport.configureBranch(
            env = EnvFixtures.gitHub()
        )
        assertEquals("release-1.51", branch.name)
    }

    @Test
    @AsAdminTest
    fun `GitHub CI engine getting the build suffix from the native environment`() {
        gitHubConfiguration()
        val build = configTestSupport.configureBuild(
            env = EnvFixtures.gitHub(),
        )
        assertTrue(build.name.endsWith("-96"), "Build name contains the run number: ${build.name}")
    }

    @Test
    @AsAdminTest
    fun `GitHub CI engine setting the workflow link on the build`() {
        gitHubConfiguration()
        val build = configTestSupport.configureBuild(
            env = EnvFixtures.gitHub(),
        )
        assertNotNull(
            propertyService.getPropertyValue(
                entity = build,
                propertyTypeClass = BuildGitHubWorkflowRunPropertyType::class.java,
            ),
            "Build workflow run property is set"
        ) {
            val workflow = it.workflows.single()
            assertEquals(EnvFixtures.GITHUB_RUN_ID, workflow.runId)
            assertEquals("https://github.com/yontrack/yontrack/actions/runs/${EnvFixtures.GITHUB_RUN_ID}", workflow.url)
            assertEquals(EnvFixtures.GITHUB_WORKFLOW, workflow.name)
            assertEquals(96, workflow.runNumber)
            assertEquals(false, workflow.running)
            assertEquals(EnvFixtures.GITHUB_EVENT_NAME, workflow.event)
        }
    }

}