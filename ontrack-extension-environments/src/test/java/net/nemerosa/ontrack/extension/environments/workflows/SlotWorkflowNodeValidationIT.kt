package net.nemerosa.ontrack.extension.environments.workflows

import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowNode
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowValidationException
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorConfigException
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A slot workflow is saved through a path which used to store the workflow straight after the security
 * check, so the data of its nodes was never validated: an unusable node configuration only surfaced at
 * deployment time, when it is too late to correct it.
 */
@AsAdminTest
class SlotWorkflowNodeValidationIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotWorkflowService: SlotWorkflowService

    @Test
    fun `Adding a slot workflow whose node data is not valid is rejected`() {
        slotTestSupport.withSlot { slot ->
            assertFailsWith<WorkflowNodeExecutorConfigException> {
                slotWorkflowService.addSlotWorkflow(
                    slotWorkflow(slot, invalidWorkflow())
                )
            }
            assertEquals(
                emptyList(),
                slotWorkflowService.getSlotWorkflowsBySlot(slot),
                "Workflow has not been saved"
            )
        }
    }

    @Test
    fun `Adding a slot workflow naming an unknown executor is rejected`() {
        slotTestSupport.withSlot { slot ->
            val ex = assertFailsWith<WorkflowValidationException> {
                slotWorkflowService.addSlotWorkflow(
                    slotWorkflow(
                        slot,
                        Workflow(
                            name = uid("w-"),
                            nodes = listOf(
                                WorkflowNode(
                                    id = "test",
                                    executorId = "no-such-executor",
                                    data = mapOf("text" to "Test").asJson(),
                                )
                            )
                        )
                    )
                )
            }
            assertTrue(
                """"no-such-executor" not found""" in (ex.message ?: ""),
                "Unknown executor is named in the error: ${ex.message}"
            )
            assertEquals(
                emptyList(),
                slotWorkflowService.getSlotWorkflowsBySlot(slot),
                "Workflow has not been saved"
            )
        }
    }

    @Test
    fun `Updating a slot workflow with node data which is not valid is rejected`() {
        slotTestSupport.withSlot { slot ->
            val slotWorkflow = slotWorkflow(slot, SlotWorkflowTestFixtures.testWorkflow())
            slotWorkflowService.addSlotWorkflow(slotWorkflow)

            assertFailsWith<WorkflowNodeExecutorConfigException> {
                slotWorkflowService.updateSlotWorkflow(
                    slotWorkflow.withWorkflow(invalidWorkflow())
                )
            }

            assertEquals(
                slotWorkflow.workflow.name,
                slotWorkflowService.getSlotWorkflowById(slotWorkflow.id).workflow.name,
                "Workflow has not been updated"
            )
        }
    }

    @Test
    fun `Adding a slot workflow whose node data is valid is accepted`() {
        slotTestSupport.withSlot { slot ->
            slotWorkflowService.addSlotWorkflow(
                slotWorkflow(slot, SlotWorkflowTestFixtures.testWorkflow())
            )
            assertEquals(
                1,
                slotWorkflowService.getSlotWorkflowsBySlot(slot).size,
                "Workflow has been saved"
            )
        }
    }

    private fun slotWorkflow(slot: Slot, workflow: Workflow) =
        SlotWorkflow(
            slot = slot,
            trigger = SlotPipelineStatus.RUNNING,
            workflow = workflow,
        )

    /**
     * The `mock` executor requires a non-blank text, so this workflow parses but does not validate.
     */
    private fun invalidWorkflow() = Workflow(
        name = uid("w-"),
        nodes = listOf(
            WorkflowNode(
                id = "test",
                executorId = "mock",
                data = mapOf("text" to "").asJson(),
            )
        )
    )


    /**
     * `slot-pipeline-creation` is the other executor whose validation is stricter than its parsing, and the
     * one which actually occurs in stored slot workflows. A blank environment could never have deployed —
     * `execute` fails to resolve it — so rejecting it at save time only moves the failure earlier.
     */
    @Test
    fun `Adding a slot workflow whose pipeline-creation node has no environment is rejected`() {
        slotTestSupport.withSlot { slot ->
            assertFailsWith<WorkflowNodeExecutorConfigException> {
                slotWorkflowService.addSlotWorkflow(
                    slotWorkflow(
                        slot,
                        Workflow(
                            name = uid("w-"),
                            nodes = listOf(
                                WorkflowNode(
                                    id = "creation",
                                    executorId = "slot-pipeline-creation",
                                    data = mapOf("environment" to "").asJson(),
                                )
                            )
                        )
                    )
                )
            }
            assertEquals(
                emptyList(),
                slotWorkflowService.getSlotWorkflowsBySlot(slot),
                "Workflow has not been saved"
            )
        }
    }

    @Test
    fun `Adding a slot workflow whose pipeline-creation node names an environment is accepted`() {
        slotTestSupport.withSlot { slot ->
            slotWorkflowService.addSlotWorkflow(
                slotWorkflow(
                    slot,
                    Workflow(
                        name = uid("w-"),
                        nodes = listOf(
                            WorkflowNode(
                                id = "creation",
                                executorId = "slot-pipeline-creation",
                                data = mapOf("environment" to slot.environment.name).asJson(),
                            )
                        )
                    )
                )
            )
            assertEquals(
                1,
                slotWorkflowService.getSlotWorkflowsBySlot(slot).size,
                "Workflow has been saved"
            )
        }
    }

}
