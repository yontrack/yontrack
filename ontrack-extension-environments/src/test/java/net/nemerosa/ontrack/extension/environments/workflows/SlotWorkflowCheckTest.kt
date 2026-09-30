package net.nemerosa.ontrack.extension.environments.workflows

import net.nemerosa.ontrack.extension.environments.SlotDeploymentCheckState
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlotWorkflowCheckTest {

    @Test
    fun `A workflow which has not started yet is pending`() {
        val check = SlotWorkflowServiceImpl.slotWorkflowCheck(status = null, overridden = false)
        assertEquals(SlotDeploymentCheckState.PENDING, check.state)
        assertEquals("Workflow has not started", check.reason)
    }

    @Test
    fun `Workflow checks by status`() {
        val expected = mapOf(
            WorkflowInstanceStatus.STARTED to (SlotDeploymentCheckState.PENDING to "Workflow has started"),
            WorkflowInstanceStatus.RUNNING to (SlotDeploymentCheckState.PENDING to "Workflow is running"),
            WorkflowInstanceStatus.STOPPED to (SlotDeploymentCheckState.FAILED to "Workflow has been stopped"),
            WorkflowInstanceStatus.ERROR to (SlotDeploymentCheckState.FAILED to "Workflow is in error"),
            WorkflowInstanceStatus.SUCCESS to (SlotDeploymentCheckState.OK to null),
        )
        // Every status is covered
        assertEquals(WorkflowInstanceStatus.entries.toSet(), expected.keys)
        expected.forEach { (status, stateAndReason) ->
            val (state, reason) = stateAndReason
            val check = SlotWorkflowServiceImpl.slotWorkflowCheck(status = status, overridden = false)
            assertEquals(state, check.state, "State for $status")
            assertEquals(reason, check.reason, "Reason for $status")
        }
    }

    @Test
    fun `An overridden workflow is OK whatever its status`() {
        (WorkflowInstanceStatus.entries + listOf(null)).forEach { status ->
            val check = SlotWorkflowServiceImpl.slotWorkflowCheck(status = status, overridden = true)
            assertEquals(SlotDeploymentCheckState.OK, check.state, "State for $status")
            assertTrue(check.overridden)
        }
    }

}
