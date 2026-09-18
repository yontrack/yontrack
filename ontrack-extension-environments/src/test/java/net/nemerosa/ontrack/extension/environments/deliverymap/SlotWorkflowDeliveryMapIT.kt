package net.nemerosa.ontrack.extension.environments.deliverymap

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflow
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowService
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowTestFixtures
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowTestSupport
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.model.structure.Branch
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The workflow half of the slot side of a delivery map, from the configured slot workflow to the
 * GraphQL field.
 *
 * `@QueueNoAsync` so that a workflow runs where it is started rather than on the queue: the map
 * shows what a run made of a gate, and a test racing an asynchronous workflow proves nothing.
 */
@QueueNoAsync
class SlotWorkflowDeliveryMapIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotWorkflowTestSupport: SlotWorkflowTestSupport

    @Autowired
    private lateinit var slotWorkflowService: SlotWorkflowService

    @Test
    fun `A candidate workflow which has never run is drawn as a gate into its slot`() {
        // The most useful thing the map can say about a slot nothing ever deployed to
        asAdmin {
            val slot = slotTestSupport.slot()
            val slotWorkflow = addSlotWorkflow(slot, SlotPipelineStatus.CANDIDATE)
            slot.project.branch {
                val map = deliveryMap(this)

                val checkpoint = map.checkpoints.single {
                    it.getRequiredTextField("type") == "slot-workflow"
                }
                assertEquals("slot-workflow:${slotWorkflow.id}", checkpoint.getRequiredTextField("id"))
                assertEquals(slotWorkflow.workflow.name, checkpoint.getRequiredTextField("name"))
                assertEquals("CANDIDATE", checkpoint.path("data").getRequiredTextField("trigger"))
                assertNull(checkpoint.path("data").path("workflowInstanceId").takeIf { !it.isNull })
                assertNull(checkpoint.path("data").path("status").takeIf { !it.isNull })

                val edge = map.edges.single { it.getRequiredTextField("kind") == "REQUIRES" }
                assertEquals("slot-workflow:${slotWorkflow.id}", edge.getRequiredTextField("source"))
                assertEquals("slot:${slot.id}", edge.getRequiredTextField("target"))
            }
        }
    }

    @Test
    fun `A done workflow is emitted by its slot rather than gating it`() {
        asAdmin {
            val slot = slotTestSupport.slot()
            val slotWorkflow = addSlotWorkflow(slot, SlotPipelineStatus.DONE)
            slot.project.branch {
                val edge = deliveryMap(this).edges.single { it.getRequiredTextField("kind") == "EMITS" }
                assertEquals("slot:${slot.id}", edge.getRequiredTextField("source"))
                assertEquals("slot-workflow:${slotWorkflow.id}", edge.getRequiredTextField("target"))
            }
        }
    }

    @Test
    fun `A workflow which has run names its instance and its status`() {
        asAdmin {
            val slot = slotTestSupport.slot()
            val slotWorkflow = addSlotWorkflow(slot, SlotPipelineStatus.CANDIDATE)
            // The gate has to pass before the deployment can start at all, which is exactly what
            // makes a CANDIDATE workflow a prerequisite on the map
            val pipeline = slotTestSupport.createPipeline(branchName = "main", slot = slot)
            // To succeed, not merely to finish: the deployment below can only run once the gate has
            // passed, and the checkpoint asserted at the end is the workflow's SUCCESS
            slotWorkflowTestSupport.waitForSlotWorkflowsToSucceed(pipeline, SlotPipelineStatus.CANDIDATE)
            slotTestSupport.runAndFinishDeployment(pipeline)

            val instance = slotWorkflowService.getSlotWorkflowInstancesByPipeline(pipeline)
                .single { it.slotWorkflow.id == slotWorkflow.id }

            val checkpoint = deliveryMap(pipeline.build.branch).checkpoints.single {
                it.getRequiredTextField("type") == "slot-workflow"
            }
            assertEquals(
                instance.workflowInstance.id,
                checkpoint.path("data").getRequiredTextField("workflowInstanceId"),
            )
            assertEquals("SUCCESS", checkpoint.path("data").getRequiredTextField("status"))
        }
    }

    @Test
    fun `A slot with no workflow draws none`() {
        asAdmin {
            val slot = slotTestSupport.slot()
            slot.project.branch {
                assertTrue(
                    deliveryMap(this).checkpoints.none {
                        it.getRequiredTextField("type") == "slot-workflow"
                    }
                )
            }
        }
    }

    private fun addSlotWorkflow(slot: Slot, trigger: SlotPipelineStatus): SlotWorkflow {
        val slotWorkflow = SlotWorkflow(
            slot = slot,
            trigger = trigger,
            workflow = SlotWorkflowTestFixtures.testWorkflow(),
        )
        slotWorkflowService.addSlotWorkflow(slotWorkflow)
        return slotWorkflow
    }

    private data class RenderedMap(
        val checkpoints: List<JsonNode>,
        val edges: List<JsonNode>,
    )

    private fun deliveryMap(branch: Branch): RenderedMap =
        run(
            """
                {
                    branch(id: ${branch.id}) {
                        deliveryMap {
                            checkpoints { id type name data }
                            edges { id kind source target }
                        }
                    }
                }
            """
        ).let { data ->
            val map = data.path("branch").path("deliveryMap")
            RenderedMap(
                checkpoints = map.path("checkpoints").toList(),
                edges = map.path("edges").toList(),
            )
        }

}
