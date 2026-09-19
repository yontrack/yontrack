package net.nemerosa.ontrack.extension.gitlab

import net.nemerosa.ontrack.extension.gitlab.model.GitLabIssueServiceConfiguration
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The GitLab issue service against a real instance of Yontrack: what makes it selectable is the set of
 * GitLab project properties the instance already carries.
 */
class GitLabIssueServiceExtensionIT : AbstractGitLabTestSupport() {

    @Autowired
    private lateinit var issueServiceExtension: GitLabIssueServiceExtension

    @Test
    fun `A configured GitLab project makes its issues selectable as an issue service`() {
        asAdmin {
            val config = gitLabConfig()
            val repository = "nemerosa/${uid("r")}"
            project {
                setGitLabProperty(config.name, repository)
            }
            val configurations = issueServiceExtension.getConfigurationList()
            val found = configurations.find { it.name == "${config.name}:$repository" }
            assertNotNull(found, "Expected ${config.name}:$repository among ${configurations.map { it.name }}") {
                assertEquals(GitLabIssueServiceExtension.GITLAB_SERVICE_ID, it.serviceId)
                assertTrue(it is GitLabIssueServiceConfiguration)
                assertEquals(repository, (it as GitLabIssueServiceConfiguration).repository)
            }
        }
    }

    @Test
    fun `Two Yontrack projects following the same GitLab project give only one issue service`() {
        asAdmin {
            val config = gitLabConfig()
            val repository = "nemerosa/${uid("r")}"
            project { setGitLabProperty(config.name, repository) }
            project { setGitLabProperty(config.name, repository) }
            assertEquals(
                1,
                issueServiceExtension.getConfigurationList().count { it.name == "${config.name}:$repository" },
            )
        }
    }

    @Test
    fun `A GitLab issue service can be selected through GraphQL`() {
        asAdmin {
            val config = gitLabConfig()
            val repository = "nemerosa/${uid("r")}"
            project { setGitLabProperty(config.name, repository) }
            run(
                """
                    {
                        issueServiceConfigurations {
                            id
                            name
                            serviceId
                        }
                    }
                """
            ) { data ->
                val id = "${GitLabIssueServiceExtension.GITLAB_SERVICE_ID}//${config.name}:$repository"
                val found = data.path("issueServiceConfigurations").find { it.path("id").asText() == id }
                assertNotNull(found, "Expected $id in the list of issue services") {
                    assertEquals("${config.name}:$repository (GitLab)", it.path("name").asText())
                    assertEquals(GitLabIssueServiceExtension.GITLAB_SERVICE_ID, it.path("serviceId").asText())
                }
            }
        }
    }

}
