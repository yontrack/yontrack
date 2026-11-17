package net.nemerosa.ontrack.kdsl.acceptance.tests.notifications.http

import net.nemerosa.ontrack.common.waitFor
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.acceptance.tests.workflows.AbstractACCDSLWorkflowsTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.workflows.WorkflowTestSupport
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.ProjectEntityType
import net.nemerosa.ontrack.kdsl.spec.Build
import net.nemerosa.ontrack.kdsl.spec.configurations.configurations
import net.nemerosa.ontrack.kdsl.spec.extension.notifications.NotificationRecord
import net.nemerosa.ontrack.kdsl.spec.extension.notifications.http.HttpAuthentication
import net.nemerosa.ontrack.kdsl.spec.extension.notifications.http.HttpConfiguration
import net.nemerosa.ontrack.kdsl.spec.extension.notifications.http.HttpMockMgt
import net.nemerosa.ontrack.kdsl.spec.extension.notifications.http.HttpNotificationChannelConfig
import net.nemerosa.ontrack.kdsl.spec.extension.notifications.http.http
import net.nemerosa.ontrack.kdsl.spec.extension.notifications.notifications
import net.nemerosa.ontrack.kdsl.spec.extension.workflows.WorkflowInstanceNodeStatus
import net.nemerosa.ontrack.kdsl.spec.extension.workflows.WorkflowInstanceStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The `http` channel against the mock deployment API of the instance itself, which the `dev` profile
 * of the KDSL stack enables.
 *
 * The polls are real: their interval is 5 seconds at least, so the deployments run for a few seconds
 * only.
 */
class ACCHttpNotifications : AbstractACCDSLWorkflowsTestSupport() {

    @Test
    fun `The mock deployment API runs a deployment, then gives its outcome`() {
        val mock = ontrack.notifications.http.mock

        val succeeded = mock.createDeployment(durationSeconds = 0)
        assertEquals("RUNNING", succeeded.status, "A deployment is created running")
        assertEquals("SUCCEEDED", mock.deployment(succeeded.id)?.status)

        val failed = mock.createDeployment(outcome = "FAILED", durationSeconds = 0)
        assertEquals("RUNNING", failed.status)
        assertEquals("FAILED", mock.deployment(failed.id)?.status)

        val running = mock.createDeployment(durationSeconds = 600)
        assertEquals("RUNNING", mock.deployment(running.id)?.status)
    }

    @Test
    fun `A POST polled on its location until the deployment has succeeded`() {
        val config = mockHttpConfiguration()
        val record = notifyOnPromotion(
            HttpNotificationChannelConfig(
                config = config.name,
                method = HttpNotificationChannelConfig.POST,
                path = "/deployments",
                body = mapOf(
                    "version" to "\${build}",
                    "durationSeconds" to 2,
                ),
                successCondition = SUCCESS_CONDITION,
                failureCondition = FAILURE_CONDITION,
                statusUrl = STATUS_URL,
                intervalSeconds = 5,
                timeoutSeconds = 60,
            )
        )

        assertEquals("OK", record.result.type, "Notification succeeded: ${record.result.message}")
        val output = assertNotNull(record.result.output, "Output")
        assertEquals("POST", output.path("method").asText())
        assertEquals("${config.url}/deployments", output.path("url").asText())
        assertEquals(200, output.path("status").asInt(), "Last response is the one of the poll")
        assertEquals("SUCCEEDED", output.path("body").path("status").asText())
        assertEquals("SUCCESS", output.path("condition").asText())
        assertEquals(2, output.path("attempts").asInt(), "The POST, then one poll")
        val id = output.path("body").path("id").asText()
        assertEquals("${config.url}/deployments/$id", output.path("statusUrl").asText())
    }

    @Test
    fun `A deployment which fails makes the notification fail`() {
        val config = mockHttpConfiguration()
        val record = notifyOnPromotion(
            HttpNotificationChannelConfig(
                config = config.name,
                method = HttpNotificationChannelConfig.POST,
                path = "/deployments",
                body = mapOf(
                    "outcome" to "FAILED",
                    "durationSeconds" to 1,
                ),
                successCondition = SUCCESS_CONDITION,
                failureCondition = FAILURE_CONDITION,
                statusUrl = STATUS_URL,
                intervalSeconds = 5,
                timeoutSeconds = 60,
            )
        )

        assertEquals("ERROR", record.result.type)
        assertTrue(
            record.result.message?.contains("the failure condition `$FAILURE_CONDITION` holds") == true,
            "The failure condition is named: ${record.result.message}"
        )
        val output = assertNotNull(record.result.output, "Output")
        assertEquals("FAILED", output.path("body").path("status").asText())
        assertEquals("FAILURE", output.path("condition").asText())
    }

    @Test
    fun `A deployment still running when the timeout expires makes the notification fail`() {
        val config = mockHttpConfiguration()
        val record = notifyOnPromotion(
            HttpNotificationChannelConfig(
                config = config.name,
                method = HttpNotificationChannelConfig.POST,
                path = "/deployments",
                body = mapOf(
                    "durationSeconds" to 600,
                ),
                successCondition = SUCCESS_CONDITION,
                failureCondition = FAILURE_CONDITION,
                statusUrl = STATUS_URL,
                // One poll after 5 s, none after 10 s
                intervalSeconds = 5,
                timeoutSeconds = 8,
            )
        )

        assertEquals("ERROR", record.result.type)
        assertTrue(
            record.result.message?.contains("timed out after 8 s") == true,
            "The notification timed out: ${record.result.message}"
        )
        val output = assertNotNull(record.result.output, "Output")
        assertEquals(2, output.path("attempts").asInt(), "The POST, then one poll")
        assertEquals("RUNNING", output.path("body").path("status").asText())
        assertEquals("PENDING", output.path("condition").asText())
    }

    @Test
    fun `A plain GET without any condition succeeds on a 2xx`() {
        val config = mockHttpConfiguration()
        val deployment = ontrack.notifications.http.mock.createDeployment(durationSeconds = 0)
        val record = notifyOnPromotion(
            HttpNotificationChannelConfig(
                config = config.name,
                path = "/deployments/${deployment.id}",
            )
        )

        assertEquals("OK", record.result.type, "Notification succeeded: ${record.result.message}")
        val output = assertNotNull(record.result.output, "Output")
        assertEquals("GET", output.path("method").asText())
        assertEquals("${config.url}/deployments/${deployment.id}", output.path("url").asText())
        assertEquals(200, output.path("status").asInt())
        assertEquals(deployment.id, output.path("body").path("id").asText())
        assertEquals("SUCCEEDED", output.path("body").path("status").asText())
        assertEquals(1, output.path("attempts").asInt(), "A single call")
        assertTrue(output.path("condition").isNull || output.path("condition").isMissingNode, "No condition")
        assertTrue(output.path("statusUrl").isNull || output.path("statusUrl").isMissingNode, "No polling")
    }

    @Test
    fun `A workflow chains the output of an http node into a later node`() {
        val config = mockHttpConfiguration()
        val group = uid("http-")
        val yaml = """
            name: Deployment
            nodes:
                - id: deploy
                  executorId: notification
                  timeout: 60
                  data:
                    channel: http
                    channelConfig:
                        config: ${config.name}
                        method: POST
                        path: /deployments
                        body:
                            version: "${'$'}{build}"
                            durationSeconds: 2
                        successCondition: "$SUCCESS_CONDITION"
                        failureCondition: "$FAILURE_CONDITION"
                        statusUrl: "$STATUS_URL"
                        intervalSeconds: 5
                        timeoutSeconds: 30
                - id: report
                  executorId: notification
                  parents:
                    - id: deploy
                  data:
                    channel: in-memory
                    channelConfig:
                        group: $group
                    template: |
                        Deployment ${'$'}{workflow.deploy?path=result.body.id} is ${'$'}{workflow.deploy?path=result.body.status}
        """.trimIndent()

        project {
            branch {
                val subName = uid("sub-")
                val pl = promotion()
                pl.subscribe(
                    name = subName,
                    channel = "workflow",
                    channelConfig = mapOf(
                        "workflow" to WorkflowTestSupport.yamlWorkflowToJson(yaml)
                    ),
                    keywords = null,
                    events = listOf("new_promotion_run"),
                )
                build {
                    promote(pl.name)

                    waitUntil(
                        task = "Getting the workflow instance id",
                        timeout = 30_000L,
                        interval = 500L,
                    ) {
                        getWorkflowInstanceId(subName).isNullOrBlank().not()
                    }
                    val instanceId = getWorkflowInstanceId(subName)
                        ?: fail("Cannot get the workflow instance ID")

                    val instance = waitUntilWorkflowFinished(
                        instanceId = instanceId,
                        timeout = 90_000L,
                    )
                    assertEquals(WorkflowInstanceStatus.SUCCESS, instance.status)
                    val nodeStatuses = instance.nodesExecutions.associate { it.id to it.status }
                    assertEquals(WorkflowInstanceNodeStatus.SUCCESS, nodeStatuses["deploy"])
                    assertEquals(WorkflowInstanceNodeStatus.SUCCESS, nodeStatuses["report"])

                    val deployOutput = assertNotNull(instance.getExecutionOutput("deploy"), "Output of the http node")
                    val id = deployOutput.path("result").path("body").path("id").asText()
                    assertTrue(id.isNotBlank(), "The http node has the id of the deployment")

                    waitUntil(
                        task = "Report of the deployment",
                        timeout = 10_000L,
                        interval = 500L,
                    ) {
                        ontrack.notifications.inMemory.group(group).firstOrNull()?.trim() ==
                                "Deployment $id is SUCCEEDED"
                    }
                }
            }
        }
    }

    /**
     * HTTP configuration on the mock deployment API of the instance, as the backend reaches itself,
     * authenticated with the token of the tests.
     */
    private fun mockHttpConfiguration(): HttpConfiguration {
        val config = HttpConfiguration(
            name = uid("http-"),
            url = "${ontractConnectionProperties.internalUrl.trimEnd('/')}${HttpMockMgt.PATH}",
            authentication = HttpAuthentication.header(
                name = "X-Ontrack-Token",
                value = ontractConnectionProperties.token,
            ),
        )
        ontrack.configurations.http.create(config)
        return config
    }

    /**
     * Subscribes a promotion to the `http` channel, promotes a build, and waits for the notification
     * to be over.
     */
    private fun notifyOnPromotion(channelConfig: HttpNotificationChannelConfig): NotificationRecord {
        var record: NotificationRecord? = null
        project {
            branch {
                val pl = promotion()
                pl.subscribe(
                    name = uid("sub-"),
                    channel = HttpNotificationChannelConfig.CHANNEL,
                    channelConfig = channelConfig,
                    keywords = null,
                    events = listOf("new_promotion_run"),
                )
                build {
                    val run = promote(pl.name)
                    record = waitForRecord(this, run.id.toInt())
                }
            }
        }
        return record ?: fail("No notification record")
    }

    private fun waitForRecord(build: Build, runId: Int): NotificationRecord =
        waitFor(
            message = "Waiting for the http notification on build ${build.name} to be over",
            interval = 500.milliseconds,
            timeout = 60.seconds,
        ) {
            ontrack.notifications.notificationRecords(
                channel = HttpNotificationChannelConfig.CHANNEL,
                projectEntityType = ProjectEntityType.PROMOTION_RUN,
                projectEntityId = runId,
            ).firstOrNull()
        } until { record ->
            record.result.type !in runningTypes
        }

    companion object {
        private const val SUCCESS_CONDITION = "response.body.status == 'SUCCEEDED'"
        private const val FAILURE_CONDITION = "response.body.status == 'FAILED'"
        private const val STATUS_URL = "response.headers['location']"

        private val runningTypes = setOf("ONGOING", "ASYNC")
    }
}
