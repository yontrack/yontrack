package net.nemerosa.ontrack.extension.github.ui.graphql

import net.nemerosa.ontrack.extension.github.AbstractGitHubTestSupport
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubConfigurationsMutationsIT : AbstractGitHubTestSupport() {

    @Autowired
    private lateinit var gitHubConfigurationService: GitHubConfigurationService

    @Test
    fun `Creating a GitHub configuration does not send the workflow ID by default`() {
        val name = uid("GH")
        asAdmin {
            withDisabledConfigurationTest {
                run(
                    """
                        mutation {
                            createGitHubConfiguration(input: {
                                name: "$name",
                                url: null,
                                oauth2Token: "some-token",
                            }) {
                                configuration {
                                    workflowSendId
                                }
                                errors {
                                    message
                                }
                            }
                        }
                    """
                ) { data ->
                    checkGraphQLUserErrors(data, "createGitHubConfiguration") { node ->
                        assertFalse(node.path("configuration").path("workflowSendId").asBoolean(true))
                    }
                }
            }
            assertFalse(gitHubConfigurationService.getConfiguration(name).workflowSendId)
        }
    }

    @Test
    fun `Creating a GitHub configuration sending the workflow ID`() {
        val name = uid("GH")
        asAdmin {
            withDisabledConfigurationTest {
                run(
                    """
                        mutation {
                            createGitHubConfiguration(input: {
                                name: "$name",
                                url: null,
                                oauth2Token: "some-token",
                                workflowSendId: true,
                            }) {
                                configuration {
                                    workflowSendId
                                }
                                errors {
                                    message
                                }
                            }
                        }
                    """
                ) { data ->
                    checkGraphQLUserErrors(data, "createGitHubConfiguration") { node ->
                        assertTrue(node.path("configuration").path("workflowSendId").asBoolean())
                    }
                }
            }
            assertTrue(gitHubConfigurationService.getConfiguration(name).workflowSendId)
        }
    }

}
