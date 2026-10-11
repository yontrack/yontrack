package net.nemerosa.ontrack.extension.workflows.recovery

/**
 * What the recovery does with an unfinished node.
 */
sealed interface WorkflowNodeRecoveryAction {

    /**
     * The node is alive, or not abandoned yet.
     */
    data object None : WorkflowNodeRecoveryAction

    /**
     * The node has done nothing yet: it is dispatched again.
     */
    data object Resume : WorkflowNodeRecoveryAction

    /**
     * The node cannot be resumed: it is put in error, and its instance stopped.
     */
    data class Fail(val message: String) : WorkflowNodeRecoveryAction
}
