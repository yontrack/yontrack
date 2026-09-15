package net.nemerosa.ontrack.kdsl.acceptance.tests.bitbucket.cloud

import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.acceptance.tests.workflows.AbstractACCDSLWorkflowsTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.workflows.WorkflowTestSupport
import net.nemerosa.ontrack.kdsl.spec.configurations.configurations
import net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud.BitbucketCloudConfiguration
import net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud.BitbucketPipelinesNotificationChannelConfig
import net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud.bitbucketCloud
import net.nemerosa.ontrack.kdsl.spec.extension.bitbucket.cloud.mock.mock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The `bitbucket-pipelines` channel, through its `mock-bitbucket-pipelines` twin.
 */
class ACCBitbucketPipelinesNotifications : AbstractACCDSLWorkflowsTestSupport() {

    private fun createConfig(): String {
        val name = uid("bbc_")
        ontrack.configurations.bitbucketCloud.create(
            BitbucketCloudConfiguration(
                name = name,
                authType = BitbucketCloudConfiguration.ACCESS_TOKEN,
                token = "any",
            )
        )
        return name
    }

    @Test
    fun `Bitbucket pipeline triggered on promotion with templated variables`() {
        val config = createConfig()
        val repository = uid("repo_")

        project {
            branch {
                val pl = promotion()
                pl.subscribe(
                    name = "Test",
                    channel = BitbucketPipelinesNotificationChannelConfig.MOCK_CHANNEL,
                    channelConfig = BitbucketPipelinesNotificationChannelConfig(
                        config = config,
                        workspace = "ws",
                        repository = repository,
                        branch = "main",
                        pipeline = "yontrack-echo",
                        variables = listOf(
                            BitbucketPipelinesNotificationChannelConfig.Variable("PROMOTION", "\${promotionLevel}"),
                        ),
                    ),
                    keywords = null,
                    events = listOf("new_promotion_run"),
                )
                build {
                    promote(pl.name)
                    waitUntil(timeout = 10_000, interval = 500L) {
                        ontrack.bitbucketCloud.mock.pipelineRuns(config, "ws", repository).isNotEmpty()
                    }
                    val run = ontrack.bitbucketCloud.mock.pipelineRuns(config, "ws", repository).single()
                    assertEquals("main", run.branch)
                    assertEquals("yontrack-echo", run.pipeline)
                    assertEquals(mapOf("PROMOTION" to pl.name), run.variables)
                }
            }
        }
    }

    @Test
    fun `Bitbucket pipeline as a workflow node, waiting for its completion`() {
        val config = createConfig()
        val repository = uid("repo_")

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
                            channel: ${BitbucketPipelinesNotificationChannelConfig.MOCK_CHANNEL}
                            channelConfig:
                                config: $config
                                workspace: ws
                                repository: $repository
                                branch: main
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
                    assertEquals("SUCCESSFUL", result?.path("state")?.asText())
                    assertEquals(repository, result?.path("repository")?.asText())

                    val run = ontrack.bitbucketCloud.mock.pipelineRuns(config, "ws", repository).single()
                    assertEquals(null, run.pipeline)
                    assertEquals(name, run.variables["VERSION"])
                }
            }
        }
    }

}
