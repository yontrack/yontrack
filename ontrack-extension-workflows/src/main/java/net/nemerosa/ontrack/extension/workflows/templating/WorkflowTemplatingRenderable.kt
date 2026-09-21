package net.nemerosa.ontrack.extension.workflows.templating

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceNodeStatus
import net.nemerosa.ontrack.json.JsonPathUtils
import net.nemerosa.ontrack.model.events.EventRenderer
import net.nemerosa.ontrack.model.templating.*

class WorkflowTemplatingRenderable(
    private val workflowInstance: WorkflowInstance,
) : TemplatingRenderable {

    override fun render(field: String?, config: TemplatingSourceConfig, renderer: EventRenderer): String =
        if (field.isNullOrBlank()) {
            throw TemplatingRenderableFieldRequiredException()
        } else {
            val (nodeId, path) = extractParameters(field, config)
            // Field == node ID
            val node = workflowInstance.getNode(nodeId)
            // Checking the node state
            if (node.status != WorkflowInstanceNodeStatus.SUCCESS) {
                throw TemplatingGeneralException("Node $nodeId is not in SUCCESS state and cannot be used.")
            } else if (node.output == null) {
                throw TemplatingGeneralException("Node $nodeId has no output.")
            }
            // Getting the raw JSON data from the node output
            val data: JsonNode? = JsonPathUtils.get(node.output, path)
            // Only text is supported
            if (data == null || data.isNull) {
                ""
            } else if (data.isTextual) {
                data.asText()
            } else {
                throw TemplatingGeneralException("Node $nodeId output does not contain a text node at $path")
            }
        }

    private fun extractParameters(field: String, config: TemplatingSourceConfig): Pair<String, String> {
        if (field.contains(".")) {
            val nodeId = field.substringBefore(".")
            val path = field.substringAfter(".")
            return nodeId to path
        } else {
            val nodeId = field
            // We require the path to the data to get
            val path = config.getRequiredString(WorkflowTemplatingRenderableParameters::path.name)
            // OK
            return nodeId to path
        }
    }

}