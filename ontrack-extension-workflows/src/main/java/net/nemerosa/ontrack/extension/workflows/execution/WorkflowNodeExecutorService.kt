package net.nemerosa.ontrack.extension.workflows.execution

import net.nemerosa.ontrack.extension.workflows.definition.Workflow

interface WorkflowNodeExecutorService {

    val executors: List<WorkflowNodeExecutor>

    fun getExecutor(executorId: String): WorkflowNodeExecutor

    fun findExecutor(executorId: String): WorkflowNodeExecutor?

    /**
     * Validates the data of every node of the [workflow] against the executor it names.
     *
     * An executor whose configuration is not usable has to be rejected while the user is still looking
     * at it, not at the moment the node runs.
     *
     * **This is only half of what a save must check, so a save path must not call it.** It says nothing
     * about the shape of the graph, so on its own it accepts a workflow which can never run — the bug
     * fixed by #1743. [validateWorkflowFully] is the pair, and is what every path persisting a workflow
     * calls. This one stays public for [net.nemerosa.ontrack.extension.workflows.registry.WorkflowRegistry.validateJsonWorkflow],
     * which needs the two halves apart in order to report their errors rather than throw them.
     *
     * @param workflow Workflow whose nodes must be validated
     */
    fun validateWorkflowNodes(workflow: Workflow)

}
