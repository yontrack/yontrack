package net.nemerosa.ontrack.kdsl.acceptance.tests.gitlab

import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.acceptance.tests.workflows.AbstractACCDSLWorkflowsTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.workflows.WorkflowTestSupport
import net.nemerosa.ontrack.kdsl.spec.configurations.configurations
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.GitLabConfiguration
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.GitLabPipelineNotificationChannelConfig
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.gitLab
import net.nemerosa.ontrack.kdsl.spec.extension.gitlab.mock.mock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The `gitlab-pipeline` channel, through its `mock-gitlab-pipeline` twin.
 */
class ACCGitLabPipelineNotifications : AbstractACCDSLWorkflowsTestSupport() {

    private fun createConfig(): String {
        val name = uid("gl_")
        ontrack.configurations.gitLab.create(
            GitLabConfiguration(
                name = name,
                url = "https://gitlab.com",
                token = "any",
            )
        )
        return name
    }

    @Test
    fun `GitLab pipeline triggered on promotion with templated variables`() {
        val config = createConfig()
        val gitLabProject = "group/${uid("p_")}"

        project {
            branch {
                val pl = promotion()
                pl.subscribe(
                    name = "Test",
                    channel = GitLabPipelineNotificationChannelConfig.MOCK_CHANNEL,
                    channelConfig = GitLabPipelineNotificationChannelConfig(
                        config = config,
                        project = gitLabProject,
                        ref = "main",
                        variables = listOf(
                            GitLabPipelineNotificationChannelConfig.Variable("PROMOTION", "\${promotionLevel}"),
                        ),
                    ),
                    keywords = null,
                    events = listOf("new_promotion_run"),
                )
                build {
                    promote(pl.name)
                    waitUntil(timeout = 10_000, interval = 500L) {
                        ontrack.gitLab.mock.pipelineRuns(config, gitLabProject).isNotEmpty()
                    }
                    val run = ontrack.gitLab.mock.pipelineRuns(config, gitLabProject).single()
                    assertEquals("main", run.ref)
                    assertEquals(gitLabProject, run.project)
                    assertEquals(mapOf("PROMOTION" to pl.name), run.variables)
                }
            }
        }
    }

    @Test
    fun `GitLab pipeline as a workflow node, waiting for its completion`() {
        val config = createConfig()
        val gitLabProject = "group/${uid("p_")}"

        project {
            branch {
                val pl = promotion()

                val workflowName = uid("w-")
                val workflow = """
                    name: $workflowName
                    nodes:
                        - id: pipeline
                          executorId: notification
                          data:
                            channel: ${GitLabPipelineNotificationChannelConfig.MOCK_CHANNEL}
                            channelConfig:
                                config: $config
                                project: $gitLabProject
                                ref: main
                                callMode: SYNC
                                timeoutSeconds: 30
                                variables:
                                    - name: VERSION
                                      value: "${'$'}{build}"
                                    - name: MOCK_DURATION_SECONDS
                                      value: "1"
                """.trimIndent()

                pl.subscribe(
                    channel = "workflow",
                    channelConfig = mapOf(
                        "workflow" to WorkflowTestSupport.yamlWorkflowToJson(workflow)
                    ),
                    keywords = null,
                    events = listOf("new_promotion_run"),
                )

                build {
                    promote(pl.name)

                    val instance = waitUntilWorkflowByNameFinished(name = workflowName)

                    val result = instance.getExecutionOutput("pipeline")?.path("result")
                    assertEquals("success", result?.path("status")?.asText())
                    assertEquals(gitLabProject, result?.path("project")?.asText())

                    val run = ontrack.gitLab.mock.pipelineRuns(config, gitLabProject).single()
                    assertEquals("main", run.ref)
                    assertEquals(name, run.variables["VERSION"])
                }
            }
        }
    }

    @Test
    fun `A GitLab pipeline which fails makes the workflow node fail`() {
        val config = createConfig()
        val gitLabProject = "group/${uid("p_")}"

        project {
            branch {
                val pl = promotion()

                val workflowName = uid("w-")
                val workflow = """
                    name: $workflowName
                    nodes:
                        - id: pipeline
                          executorId: notification
                          data:
                            channel: ${GitLabPipelineNotificationChannelConfig.MOCK_CHANNEL}
                            channelConfig:
                                config: $config
                                project: $gitLabProject
                                ref: main
                                callMode: SYNC
                                timeoutSeconds: 30
                                variables:
                                    - name: MOCK_STATUS
                                      value: failed
                """.trimIndent()

                pl.subscribe(
                    channel = "workflow",
                    channelConfig = mapOf(
                        "workflow" to WorkflowTestSupport.yamlWorkflowToJson(workflow)
                    ),
                    keywords = null,
                    events = listOf("new_promotion_run"),
                )

                build {
                    promote(pl.name)

                    // The node fails, which is the point of the test
                    val instance = waitUntilWorkflowByNameFinished(
                        name = workflowName,
                        returnInstanceOnError = true,
                    )

                    val result = instance.getExecutionOutput("pipeline")?.path("result")
                    assertEquals("failed", result?.path("status")?.asText())

                    val run = ontrack.gitLab.mock.pipelineRuns(config, gitLabProject).single()
                    assertEquals(mapOf("MOCK_STATUS" to "failed"), run.variables)
                }
            }
        }
    }
}
