package net.nemerosa.ontrack.extension.workflows.definition

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.workflows.schema.WorkflowDynamicJsonSchemaProvider
import net.nemerosa.ontrack.model.docs.DocumentationList
import net.nemerosa.ontrack.model.docs.SelfDocumented
import net.nemerosa.ontrack.model.json.schema.DynamicJsonSchema

/**
 * Definition of a node in a workflow.
 *
 * @property id Unique ID of the node in its workflow.
 * @property description Description of the node in its workflow.
 * @property executorId ID of the executor to use
 * @property data Raw data associated with the node, to be used by the node executor.
 * @property parents List of the IDs of the parents for this node
 * @property timeout Timeout in seconds (5 minutes by default)
 * @property interval Interval in seconds to wait between checks of an async process inside the execution of a node. Not defined by default, using default settings.
 */
@SelfDocumented
@DynamicJsonSchema(
    discriminatorProperty = "executorId",
    configurationProperty = "data",
    provider = WorkflowDynamicJsonSchemaProvider::class,
)
data class WorkflowNode(
    @APIDescription("Unique ID of the node in its workflow.")
    val id: String,
    @APIDescription("Description of the node in its workflow.")
    val description: String? = null,
    @APIDescription("ID of the executor to use")
    val executorId: String,
    @APIDescription("Raw data associated with the node, to be used by the node executor.")
    val data: JsonNode,
    @APIDescription("List of the IDs of the parents for this node")
    @DocumentationList
    val parents: List<WorkflowParentNode> = emptyList(),
    @APIDescription("Timeout in seconds (5 minutes by default)")
    val timeout: Long = 5 * 60,
    @APIDescription("Interval in seconds to wait between checks of an async process inside the execution of a node. Not defined by default, using default settings.")
    val interval: Long? = null,
) {
    fun addParent(node: WorkflowNode) = WorkflowNode(
        id = id,
        description = description,
        executorId = executorId,
        data = data,
        parents = parents + WorkflowParentNode(node.id),
        timeout = timeout,
        interval = interval,
    )
}
