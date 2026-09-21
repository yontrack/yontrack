package net.nemerosa.ontrack.kdsl.spec.extension.workflows

import tools.jackson.databind.JsonNode

data class WorkflowInstanceNode(
    val id: String,
    val status: WorkflowInstanceNodeStatus,
    val output: JsonNode?,
    val error: String?,
)
