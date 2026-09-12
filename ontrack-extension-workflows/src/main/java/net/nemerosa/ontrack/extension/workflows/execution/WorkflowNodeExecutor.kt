package net.nemerosa.ontrack.extension.workflows.execution

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.model.extension.Extension

/**
 * Registerable workflow callback for the execution of the nodes.
 */
interface WorkflowNodeExecutor : Extension {

    /**
     * ID of the executor
     */
    val id: String

    /**
     * Display name for the executor
     */
    val displayName: String

    /**
     * Validation of the configuration for this executor.
     *
     * An implementation must be side-effect-free, must make no outbound call, and must not read any
     * object the caller may not be entitled to see: this runs on an advisory preview
     * ([net.nemerosa.ontrack.extension.workflows.registry.WorkflowRegistry.validateJsonWorkflow]) which
     * carries no authorization check of its own, for a workflow which is not saved and may never be.
     * Checking the *shape* of [data] is what belongs here; resolving what it names belongs to [execute].
     *
     * An implementation which validates a nested workflow must do it through
     * [WorkflowNodeExecutorService.validateWorkflowNodes], whose depth guard is what keeps the nesting
     * from recursing without bound.
     */
    fun validate(data: JsonNode)

    /**
     * Runs some action for a given workflow node.
     *
     * @param workflowInstance Workflow to run
     * @param workflowNodeId Workflow node
     * @return Result for the node execution
     */
    fun execute(
        workflowInstance: WorkflowInstance,
        workflowNodeId: String,
        workflowNodeExecutorResultFeedback: (output: JsonNode?) -> Unit,
    ): WorkflowNodeExecutorResult

    @APIDescription("Checks if this node executor is enabled.")
    val enabled: Boolean get() = true

}