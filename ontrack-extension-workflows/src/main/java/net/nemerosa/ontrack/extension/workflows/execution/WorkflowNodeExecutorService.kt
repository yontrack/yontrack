package net.nemerosa.ontrack.extension.workflows.execution

import net.nemerosa.ontrack.extension.workflows.definition.Workflow

interface WorkflowNodeExecutorService {

    val executors: List<WorkflowNodeExecutor>

    fun getExecutor(executorId: String): WorkflowNodeExecutor

    fun findExecutor(executorId: String): WorkflowNodeExecutor?

    /**
     * Validates the data of every node of the [workflow] against the executor it names.
     *
     * This is the check every _saving_ of a workflow must run: an executor whose configuration is not
     * usable has to be rejected while the user is still looking at it, not at the moment the node runs.
     * [net.nemerosa.ontrack.extension.workflows.definition.WorkflowValidation.validateWorkflow] is the
     * complement of this check: it looks at the structure of the workflow only, never at the node data.
     *
     * @param workflow Workflow whose nodes must be validated
     */
    fun validateWorkflowNodes(workflow: Workflow)

}
