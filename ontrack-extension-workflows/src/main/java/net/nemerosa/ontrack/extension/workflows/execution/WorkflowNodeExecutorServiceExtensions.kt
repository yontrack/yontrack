package net.nemerosa.ontrack.extension.workflows.execution

import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowValidation

/**
 * Runs the *complete* validation of a [workflow] before it is saved: its structure first, then the data
 * of each of its nodes.
 *
 * Every path which persists a workflow must go through this, and the order is part of the contract: a
 * cyclic workflow has to report the cycle, not whichever node error the executors happen to raise first
 * while looking at a graph that can never run.
 *
 * The complement is
 * [net.nemerosa.ontrack.extension.workflows.registry.WorkflowRegistry.validateJsonWorkflow], which runs
 * the same two checks but *reports* their errors instead of throwing them, because it backs the
 * advisory preview of the edition dialog.
 *
 * @param workflow Workflow about to be saved
 */
fun WorkflowNodeExecutorService.validateWorkflowFully(workflow: Workflow) {
    WorkflowValidation.validateWorkflow(workflow).throwErrorIfAny()
    validateWorkflowNodes(workflow)
}
