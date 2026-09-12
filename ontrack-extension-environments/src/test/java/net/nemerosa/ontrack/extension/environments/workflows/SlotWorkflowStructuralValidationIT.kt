package net.nemerosa.ontrack.extension.environments.workflows

import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowTestFixtures.mockNode
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowTestFixtures.slotWorkflow
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowValidationException
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The slot workflow save path used to run the *node* validation only, never
 * [net.nemerosa.ontrack.extension.workflows.definition.WorkflowValidation.validateWorkflow]. A cyclic or
 * rootless workflow was therefore accepted and persisted against a slot, and only rejected by the engine
 * at `startWorkflow` — that is, in the middle of a deployment, which is the most expensive moment to
 * discover it.
 *
 * Every structural case is run against all three ways in which a slot workflow reaches the database, so
 * that a future entry point which skips the check has no green test to hide behind.
 */
@AsAdminTest
class SlotWorkflowStructuralValidationIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotWorkflowService: SlotWorkflowService

    /**
     * The three ways a slot workflow is persisted. `saveSlotWorkflow` is the JSON mutation behind the
     * edition dialog, whose preview is advisory — nothing forces a caller through it. `updateSlotWorkflow`
     * is what CasC uses for a slot workflow which already exists.
     */
    enum class EntryPoint {
        ADD,
        SAVE_MUTATION,
        UPDATE,
    }

    @ParameterizedTest
    @EnumSource(EntryPoint::class)
    fun `A cyclic workflow is rejected`(entryPoint: EntryPoint) {
        slotTestSupport.withSlot { slot ->
            val message = assertRejected(entryPoint, slot, cyclicWorkflow())
            assertTrue("cycle" in message, "The cycle is named in the error: $message")
        }
    }

    /**
     * A rootless workflow — one where every node has a parent — is necessarily cyclic once all its parents
     * resolve, because a finite graph in which every node has a predecessor must close on itself. So the
     * cycle rule always fires before `The workflow must have at least one starting node`, and the message
     * names the cycle. The root rule is not dead, it is simply unreachable from here; what this test pins
     * is that the rootless *shape* — here a single node which is its own parent — is refused.
     */
    @ParameterizedTest
    @EnumSource(EntryPoint::class)
    fun `A rootless workflow is rejected`(entryPoint: EntryPoint) {
        slotTestSupport.withSlot { slot ->
            assertRejected(entryPoint, slot, selfParentedWorkflow())
        }
    }

    @ParameterizedTest
    @EnumSource(EntryPoint::class)
    fun `A structurally valid workflow is accepted`(entryPoint: EntryPoint) {
        slotTestSupport.withSlot { slot ->
            assertAccepted(
                entryPoint, slot,
                Workflow(
                    name = uid("w-"),
                    nodes = listOf(
                        mockNode("start"),
                        mockNode("end", parents = listOf("start")),
                    )
                )
            )
        }
    }

    @Test
    fun `Adding a slot workflow naming an unknown parent is rejected`() {
        slotTestSupport.withSlot { slot ->
            val message = assertRejected(
                EntryPoint.ADD, slot,
                Workflow(
                    name = uid("w-"),
                    nodes = listOf(
                        mockNode("start"),
                        mockNode("end", parents = listOf("no-such-node")),
                    )
                )
            )
            assertTrue(
                """"no-such-node" is not a valid node ID""" in message,
                "The unknown parent is named in the error: $message"
            )
        }
    }

    /**
     * Structural validation runs before the node validation, so a workflow which is broken on both axes
     * reports the cycle rather than an unrelated node error. Reversing that order would tell the user to
     * fix a node which is not the problem.
     */
    @Test
    fun `A cyclic workflow whose node data is also invalid reports the cycle`() {
        slotTestSupport.withSlot { slot ->
            val message = assertRejected(
                EntryPoint.ADD, slot,
                Workflow(
                    name = uid("w-"),
                    nodes = listOf(
                        mockNode("a", text = "", parents = listOf("b")),
                        mockNode("b", text = "", parents = listOf("a")),
                    )
                )
            )
            assertTrue(
                "cycle" in message,
                "The cycle is reported rather than the node data: $message"
            )
        }
    }

    /**
     * Submits [workflow] to [slot] through [entryPoint], asserts that it was refused and that nothing was
     * persisted, and returns the message of the rejection.
     *
     * The three paths cannot share one `assertFailsWith`: the mutation reports its errors in its payload
     * instead of throwing, which is precisely why it needs its own case here rather than being assumed to
     * behave like the service call underneath it.
     */
    private fun assertRejected(entryPoint: EntryPoint, slot: Slot, workflow: Workflow): String =
        when (entryPoint) {
            EntryPoint.ADD -> {
                val ex = assertFailsWith<WorkflowValidationException> {
                    slotWorkflowService.addSlotWorkflow(slotWorkflow(slot, workflow))
                }
                assertNothingSaved(slot)
                ex.message ?: ""
            }

            EntryPoint.SAVE_MUTATION -> {
                var message = ""
                run(saveSlotWorkflowMutation(slot), mapOf("workflow" to workflow.asJson())) { data ->
                    message = data.path("saveSlotWorkflow").path("errors").path(0).path("message").asText()
                }
                assertTrue(message.isNotBlank(), "The mutation reports an error")
                assertNothingSaved(slot)
                message
            }

            EntryPoint.UPDATE -> {
                val existing = existingSlotWorkflow(slot)
                val ex = assertFailsWith<WorkflowValidationException> {
                    slotWorkflowService.updateSlotWorkflow(existing.withWorkflow(workflow))
                }
                assertEquals(
                    existing.workflow.name,
                    slotWorkflowService.getSlotWorkflowById(existing.id).workflow.name,
                    "Workflow has not been updated"
                )
                ex.message ?: ""
            }
        }

    /**
     * Submits [workflow] to [slot] through [entryPoint] and asserts that it was persisted — the check the
     * three rejection cases need beside them, so that "everything is refused" cannot pass for a fix.
     */
    private fun assertAccepted(entryPoint: EntryPoint, slot: Slot, workflow: Workflow) {
        when (entryPoint) {
            EntryPoint.ADD ->
                slotWorkflowService.addSlotWorkflow(slotWorkflow(slot, workflow))

            EntryPoint.SAVE_MUTATION ->
                run(saveSlotWorkflowMutation(slot), mapOf("workflow" to workflow.asJson())) { data ->
                    checkGraphQLUserErrors(data, "saveSlotWorkflow")
                }

            EntryPoint.UPDATE ->
                slotWorkflowService.updateSlotWorkflow(
                    existingSlotWorkflow(slot).withWorkflow(workflow)
                )
        }
        assertEquals(
            listOf(workflow.name),
            slotWorkflowService.getSlotWorkflowsBySlot(slot).map { it.workflow.name },
            "Workflow has been saved"
        )
    }

    private fun existingSlotWorkflow(slot: Slot) =
        slotWorkflow(slot).also { slotWorkflowService.addSlotWorkflow(it) }

    private fun assertNothingSaved(slot: Slot) {
        assertEquals(
            emptyList(),
            slotWorkflowService.getSlotWorkflowsBySlot(slot),
            "Workflow has not been saved"
        )
    }

    private fun saveSlotWorkflowMutation(slot: Slot) = """
        mutation SaveSlotWorkflow(${'$'}workflow: JSON!) {
            saveSlotWorkflow(input: {
                slotId: "${slot.id}",
                trigger: RUNNING,
                workflow: ${'$'}workflow,
            }) {
                errors {
                    message
                }
            }
        }
    """

    private fun cyclicWorkflow() = Workflow(
        name = uid("w-"),
        nodes = listOf(
            mockNode("a", parents = listOf("b")),
            mockNode("b", parents = listOf("a")),
        )
    )

    private fun selfParentedWorkflow() = Workflow(
        name = uid("w-"),
        nodes = listOf(
            mockNode("a", parents = listOf("a")),
        )
    )
}
