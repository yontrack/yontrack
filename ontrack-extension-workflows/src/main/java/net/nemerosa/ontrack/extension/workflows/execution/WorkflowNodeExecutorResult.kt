package net.nemerosa.ontrack.extension.workflows.execution

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.events.SerializableEvent

/**
 * @property event Event to merge with the workflow's current context
 */
data class WorkflowNodeExecutorResult(
    val type: WorkflowNodeExecutorResultType,
    val message: String?,
    val output: JsonNode?,
    val event: SerializableEvent? = null,
) {
    companion object {

        fun error(message: String, output: JsonNode?) = WorkflowNodeExecutorResult(
            type = WorkflowNodeExecutorResultType.ERROR,
            message = message,
            output = output,
        )

        fun success(output: JsonNode?, event: SerializableEvent? = null) = WorkflowNodeExecutorResult(
            type = WorkflowNodeExecutorResultType.SUCCESS,
            message = null,
            output = output,
            event = event,
        )

    }
}
