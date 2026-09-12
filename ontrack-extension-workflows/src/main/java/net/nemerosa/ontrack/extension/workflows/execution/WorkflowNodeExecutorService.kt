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
     * A node may nest another workflow - a `notification` node on the `workflow` channel does - and the
     * validation of that node comes back here. The implementation counts how deep one validation has
     * gone and rejects a workflow nested past
     * [net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorServiceImpl.MAX_VALIDATION_DEPTH]
     * levels, as a validation error like any other.
     *
     * @param workflow Workflow whose nodes must be validated
     */
    fun validateWorkflowNodes(workflow: Workflow)

}
