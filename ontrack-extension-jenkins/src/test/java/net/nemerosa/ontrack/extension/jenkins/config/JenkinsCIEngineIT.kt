package net.nemerosa.ontrack.extension.jenkins.config

import net.nemerosa.ontrack.extension.config.ConfigTestSupport
import net.nemerosa.ontrack.extension.config.EnvFixtures
import net.nemerosa.ontrack.extension.jenkins.JenkinsBuildPropertyType
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfiguration
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfigurationService
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JenkinsCIEngineIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var configTestSupport: ConfigTestSupport

    @Autowired
    private lateinit var jenkinsConfigurationService: JenkinsConfigurationService

    @Test
    @AsAdminTest
    fun `Jenkins generating the project name from the Git URL`() {
        configTestSupport.withConfigAndProject(
            """
                version: v1
                configuration: {}
            """.trimIndent(),
            ci = null,
            env = EnvFixtures.jenkins(),
            expectedProjectName = "ontrack",
        ) { project, _ ->
            assertEquals("ontrack", project.name)
        }
    }

    @Test
    @AsAdminTest
    fun `Jenkins generating the project name from an environment variable`() {
        val projectNameFromEnv = uid("prj-")
        configTestSupport.withConfigAndProject(
            """
                version: v1
                configuration: {}
            """.trimIndent(),
            ci = null,
            env = EnvFixtures.jenkins(
                extraEnv = mapOf("PROJECT_NAME" to projectNameFromEnv),
            ),
            expectedProjectName = projectNameFromEnv,
        ) { project, _ ->
            assertEquals(projectNameFromEnv, project.name)
        }
    }

    @Test
    @AsAdminTest
    fun `Jenkins generating the project name from the configuration`() {
        configTestSupport.withConfigAndProject(
            """
                version: v1
                configuration:
                  defaults:
                    project:
                      name: "nemerosa"
            """.trimIndent(),
            ci = null,
            env = EnvFixtures.jenkins(
                extraEnv = mapOf(
                    "PROJECT_NAME" to "yontrack",
                    "GIT_URL" to "https://github.com/nemerosa/ontrack.git",
                )
            ),
            expectedProjectName = "nemerosa"
        ) { project, _ ->
            assertEquals("nemerosa", project.name)
        }
    }

    @Test
    @AsAdminTest
    fun `Link to the Jenkins job at build level`() {
        jenkinsConfiguration()
        val build = configTestSupport.configureBuild(
            scm = "mock",
            env = EnvFixtures.jenkins()
        )
        // Checking the Jenkins link
        assertNotNull(
            propertyService.getPropertyValue(build, JenkinsBuildPropertyType::class.java),
            "Jenkins build property is set"
        ) {
            assertEquals("https://jenkins.dev.yontrack.com/job/nemerosa/job/ontrack/job/main/23", it.url)
            assertEquals("nemerosa/ontrack/main", it.job)
            assertEquals(23, it.build)
        }
    }

    private fun jenkinsConfiguration(
        name: String = uid("jenkins-"),
        url: String = EnvFixtures.JENKINS_URL,
    ) {
        withDisabledConfigurationTest {
            val configuration = JenkinsConfiguration(
                name = name,
                url = url,
                user = "user",
                password = "password",
            )
            jenkinsConfigurationService.newConfiguration(configuration)
        }
    }

}