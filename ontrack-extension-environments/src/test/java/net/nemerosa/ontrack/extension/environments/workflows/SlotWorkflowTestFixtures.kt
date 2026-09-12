package net.nemerosa.ontrack.extension.environments.workflows

import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowNode
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowParentNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.test.TestUtils.uid

object SlotWorkflowTestFixtures {

    fun testWorkflow(
        name: String = uid("w-"),
        waitMs: Int = 0,
        error: Boolean = false,
    ) = Workflow(
        name = name,
        nodes = listOf(
            WorkflowNode(
                id = "test",
                executorId = "mock",
                description = "Mock node",
                data = mapOf(
                    "text" to "Test",
                    "waitMs" to waitMs,
                    "error" to error,
                ).asJson(),
            )
        )
    )

    fun slotWorkflow(
        slot: Slot,
        workflow: Workflow = testWorkflow(),
        trigger: SlotPipelineStatus = SlotPipelineStatus.RUNNING,
    ) = SlotWorkflow(
        slot = slot,
        trigger = trigger,
        workflow = workflow,
    )

    /**
     * A node for the `mock` executor, whose only requirement is a non-blank text. Pass a blank [text] to
     * get a node which parses but does not validate.
     */
    fun mockNode(
        id: String,
        text: String = "Text for $id",
        parents: List<String> = emptyList(),
    ) = WorkflowNode(
        id = id,
        parents = parents.map { WorkflowParentNode(it) },
        executorId = "mock",
        data = mapOf("text" to text).asJson(),
    )
}
