package net.nemerosa.ontrack.extension.workflows.execution

import net.nemerosa.ontrack.model.exceptions.InputException

/**
 * The data of a node was rejected by its executor. Whichever executor it was, the error names the node,
 * since on a workflow of several nodes, the message of the executor alone leaves the user guessing.
 */
class WorkflowNodeValidationException(
    workflowName: String,
    nodeId: String,
    cause: Exception,
) : InputException(
    cause,
    if (workflowName.isBlank()) {
        """Configuration of node "$nodeId" is not valid > ${cause.message}"""
    } else {
        """Configuration of node "$nodeId" in workflow "$workflowName" is not valid > ${cause.message}"""
    }
)
