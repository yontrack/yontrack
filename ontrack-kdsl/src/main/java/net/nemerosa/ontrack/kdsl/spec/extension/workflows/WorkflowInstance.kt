package net.nemerosa.ontrack.kdsl.spec.extension.workflows

import tools.jackson.databind.JsonNode

data class WorkflowInstance(
    val status: WorkflowInstanceStatus,
    val finished: Boolean,
    val nodesExecutions: List<WorkflowInstanceNode>,
) {
    fun getWorkflowInstanceNode(nodeId: String): WorkflowInstanceNode? {
        return nodesExecutions.firstOrNull { it.id == nodeId }
    }

    fun getExecutionOutput(nodeId: String): JsonNode? =
        getWorkflowInstanceNode(nodeId)?.output
}
