package net.nemerosa.ontrack.extension.environments.events

import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowService
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowTestSupport
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.HtmlNotificationEventRenderer
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * Deployment mutations which used to change the story of a build without posting any event (#1957).
 */
@QueueNoAsync
@AsAdminTest
class DeploymentMutationEventsIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotWorkflowTestSupport: SlotWorkflowTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var slotWorkflowService: SlotWorkflowService

    @Autowired
    private lateinit var eventQueryService: EventQueryService

    @Autowired
    private lateinit var eventTemplatingService: EventTemplatingService

    @Autowired
    private lateinit var htmlNotificationEventRenderer: HtmlNotificationEventRenderer

    @Test
    fun `Deleting a deployment posts the slot-pipeline-deleted event`() {
        slotTestSupport.withSlotPipeline { pipeline ->
            slotService.deleteDeployment(pipeline.id)
            val event = eventQueryService.getLastEvent(pipeline.build, EnvironmentsEvents.PIPELINE_DELETED)
            assertNotNull(event, "Deployment deletion event posted") {
                assertEquals(pipeline.build.id, it.entities[ProjectEntityType.BUILD]?.id)
                assertEquals(pipeline.id, it.getValue(EnvironmentsEvents.EVENT_PIPELINE_ID))
                assertEquals("1", it.getValue(EnvironmentsEvents.EVENT_PIPELINE_NUMBER))
                assertEquals("CANDIDATE", it.getValue(EnvironmentsEvents.EVENT_PIPELINE_STATUS))
                assertEquals(pipeline.slot.id, it.getValue(EnvironmentsEvents.EVENT_SLOT_ID))
                assertEquals(pipeline.slot.environment.name, it.getValue(EnvironmentsEvents.EVENT_ENVIRONMENT_NAME))
            }
        }
    }

    @Test
    fun `Overriding a slot workflow posts the slot-pipeline-workflow-overridden event`() {
        slotWorkflowTestSupport.withSlotWorkflow(
            trigger = SlotPipelineStatus.RUNNING,
            error = true,
        ) { slot, slotWorkflow ->
            val pipeline = slotTestSupport.createPipeline(slot = slot)
            slotService.runDeployment(pipeline.id, dryRun = false)
            slotWorkflowTestSupport.waitForSlotWorkflowsToFinish(pipeline, SlotPipelineStatus.RUNNING)
            val instance = slotWorkflowService.findSlotWorkflowInstanceByPipelineAndSlotWorkflow(pipeline, slotWorkflow)
                ?: fail("Could not find slot workflow instance")

            slotWorkflowService.overrideSlotWorkflowInstance(
                slotWorkflowInstanceId = instance.id,
                message = "Checked by hand with the on-call",
            )

            val event = eventQueryService.getLastEvent(pipeline.build, EnvironmentsEvents.PIPELINE_WORKFLOW_OVERRIDDEN)
            assertNotNull(event, "Workflow override event posted") {
                assertEquals(pipeline.build.id, it.entities[ProjectEntityType.BUILD]?.id)
                assertEquals(pipeline.id, it.getValue(EnvironmentsEvents.EVENT_PIPELINE_ID))
                assertEquals(slotWorkflow.id, it.getValue(EnvironmentsEvents.EVENT_SLOT_WORKFLOW_ID))
                assertEquals(instance.id, it.getValue(EnvironmentsEvents.EVENT_SLOT_WORKFLOW_INSTANCE_ID))
                assertEquals(slotWorkflow.workflow.name, it.getValue(EnvironmentsEvents.EVENT_WORKFLOW_NAME))
                assertEquals("Checked by hand with the on-call", it.getValue(EnvironmentsEvents.EVENT_OVERRIDE_MESSAGE))
                assertEquals(
                    securityService.currentUser?.name,
                    it.getValue(EnvironmentsEvents.EVENT_PIPELINE_OVERRIDING_USER)
                )
                assertEquals(
                    """Workflow ${slotWorkflow.workflow.name} of pipeline <a href="http://localhost:3000/extension/environments/pipeline/${pipeline.id}">${pipeline.fullName()}</a> has been overridden by ${securityService.currentUser?.name}.""",
                    eventTemplatingService.renderEvent(it, renderer = htmlNotificationEventRenderer)
                )
            }
        }
    }

}
