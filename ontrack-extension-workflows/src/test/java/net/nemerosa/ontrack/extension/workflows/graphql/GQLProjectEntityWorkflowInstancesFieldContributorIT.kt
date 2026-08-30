package net.nemerosa.ontrack.extension.workflows.graphql

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationSource
import net.nemerosa.ontrack.extension.notifications.mock.MockNotificationSourceDataType
import net.nemerosa.ontrack.extension.notifications.model.createData
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordingService
import net.nemerosa.ontrack.extension.notifications.recording.toNotificationRecordResult
import net.nemerosa.ontrack.extension.workflows.AbstractWorkflowTestSupport
import net.nemerosa.ontrack.extension.workflows.notifications.WorkflowNotificationChannel
import net.nemerosa.ontrack.extension.workflows.notifications.WorkflowNotificationChannelOutput
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.test.assertEquals

internal class GQLProjectEntityWorkflowInstancesFieldContributorIT : AbstractWorkflowTestSupport() {

    @Autowired
    private lateinit var notificationRecordingService: NotificationRecordingService

    @Autowired
    private lateinit var mockNotificationSource: MockNotificationSource

    @Autowired
    private lateinit var eventFactory: EventFactory

    private fun workflowYaml(name: String) = """
        name: $name
        nodes:
            - id: start
              executorId: mock
              data:
                text: Start
            - id: end
              parents:
                - id: start
              executorId: mock
              data:
                text: End
    """.trimIndent()

    /**
     * Records a `workflow` notification record pointing at [instanceId], for the promotion event
     * of the given [promotionRun].
     */
    private fun recordWorkflowNotification(promotionRun: PromotionRun, instanceId: String) {
        asAdmin {
            notificationRecordingService.record(
                NotificationRecord(
                    id = UUID.randomUUID().toString(),
                    source = mockNotificationSource.createData(MockNotificationSourceDataType(text = "test")),
                    timestamp = Time.now(),
                    channel = WorkflowNotificationChannel.TYPE,
                    channelConfig = mapOf("workflow" to mapOf("name" to "test")).asJson(),
                    event = eventFactory.newPromotionRun(promotionRun).asJson(),
                    result = NotificationResult.async(
                        WorkflowNotificationChannelOutput(workflowInstanceId = instanceId)
                    ).toNotificationRecordResult(),
                )
            )
        }
    }

    @Test
    fun `Workflows launched for a promotion run are returned`() {
        val workflowName = uid("wf-")
        val instanceId = workflowTestSupport.registerLaunchAndWaitForWorkflow(workflowYaml(workflowName))
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    build {
                        val promotionRun = promote(pl)
                        recordWorkflowNotification(promotionRun, instanceId)
                        run(
                            """{
                                promotionRuns(id: ${promotionRun.id}) {
                                    workflowInstances {
                                        id
                                        status
                                        workflow { name }
                                    }
                                }
                            }"""
                        ) { data ->
                            val instances = data.path("promotionRuns").path(0).path("workflowInstances")
                            assertEquals(1, instances.size())
                            assertEquals(instanceId, instances.path(0).path("id").asText())
                            assertEquals("SUCCESS", instances.path(0).path("status").asText())
                            assertEquals(workflowName, instances.path(0).path("workflow").path("name").asText())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Workflows launched for another entity are not returned`() {
        val instanceId = workflowTestSupport.registerLaunchAndWaitForWorkflow(workflowYaml(uid("wf-")))
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    val otherRun = build().promote(pl)
                    recordWorkflowNotification(otherRun, instanceId)
                    val promotionRun = build().promote(pl)
                    run(
                        """{
                            promotionRuns(id: ${promotionRun.id}) {
                                workflowInstances { id }
                            }
                        }"""
                    ) { data ->
                        assertEquals(
                            0,
                            data.path("promotionRuns").path(0).path("workflowInstances").size()
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Records pointing at a missing workflow instance are skipped`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    val promotionRun = build().promote(pl)
                    recordWorkflowNotification(promotionRun, uid("missing-"))
                    run(
                        """{
                            promotionRuns(id: ${promotionRun.id}) {
                                workflowInstances { id }
                            }
                        }"""
                    ) { data ->
                        assertEquals(
                            0,
                            data.path("promotionRuns").path(0).path("workflowInstances").size()
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `A user with only view access on the project sees the workflows`() {
        val instanceId = workflowTestSupport.registerLaunchAndWaitForWorkflow(workflowYaml(uid("wf-")))
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    val promotionRun = build().promote(pl)
                    recordWorkflowNotification(promotionRun, instanceId)
                    asUserWithView(this@project) {
                        run(
                            """{
                                promotionRuns(id: ${promotionRun.id}) {
                                    workflowInstances { id }
                                }
                            }"""
                        ) { data ->
                            val instances = data.path("promotionRuns").path(0).path("workflowInstances")
                            assertEquals(1, instances.size())
                            assertEquals(instanceId, instances.path(0).path("id").asText())
                        }
                    }
                }
            }
        }
    }
}
