package net.nemerosa.ontrack.extension.workflows.recovery

import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceLauncher
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceNodeStatus
import java.time.LocalDateTime

/**
 * Unfinished node, as seen by the recovery.
 *
 * @property instanceId ID of the workflow instance
 * @property nodeId ID of the node
 * @property status Status of the node
 * @property owner ID of the process which claimed the node, null if not claimed, or claimed by a
 * version of Yontrack which did not record it
 * @property dispatched Last time the node was sent to the queue, null if not recorded
 * @property instanceTimestamp Creation time of the instance
 * @property launcher Who launched the instance, null if unknown
 */
data class WorkflowNodeRecoveryCandidate(
    val instanceId: String,
    val nodeId: String,
    val status: WorkflowInstanceNodeStatus,
    val owner: String?,
    val dispatched: LocalDateTime?,
    val instanceTimestamp: LocalDateTime,
    val launcher: WorkflowInstanceLauncher?,
)
