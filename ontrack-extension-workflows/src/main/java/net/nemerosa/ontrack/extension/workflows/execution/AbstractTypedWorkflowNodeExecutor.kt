package net.nemerosa.ontrack.extension.workflows.execution

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.json.parseInto
import net.nemerosa.ontrack.model.docs.DocumentationIgnore
import net.nemerosa.ontrack.model.extension.ExtensionFeature
import kotlin.reflect.KClass

/**
 * [WorkflowNodeExecutor] implementation relying on known types for its data & output.
 *
 * @param D Type for the data
 */
abstract class AbstractTypedWorkflowNodeExecutor<D : Any>(
    @DocumentationIgnore
    override val feature: ExtensionFeature,
    override val id: String,
    override val displayName: String,
    private val dataType: KClass<D>,
) : WorkflowNodeExecutor {

    override fun execute(
        workflowInstance: WorkflowInstance,
        workflowNodeId: String,
        workflowNodeExecutorResultFeedback: (output: JsonNode?) -> Unit
    ): WorkflowNodeExecutorResult {
        val data = workflowInstance.workflow.getNode(workflowNodeId).data.parseInto(dataType)
        return execute(workflowInstance, data, workflowNodeExecutorResultFeedback)
    }

    abstract fun execute(
        workflowInstance: WorkflowInstance,
        data: D,
        workflowNodeExecutorResultFeedback: (output: JsonNode?) -> Unit,
    ): WorkflowNodeExecutorResult

}