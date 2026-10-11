package net.nemerosa.ontrack.extension.workflows.recovery

import net.nemerosa.ontrack.extension.workflows.WorkflowConfigurationProperties
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceNodeStatus
import java.time.LocalDateTime

/**
 * Decides what to do with an unfinished node.
 *
 * - a node is **abandoned** when the process which claimed it has had no heartbeat for
 *   [staleAfter][WorkflowConfigurationProperties.RecoveryConfigProperties.staleAfter], or, for a node
 *   no process claimed, when it was dispatched more than
 *   [createdStaleAfter][WorkflowConfigurationProperties.RecoveryConfigProperties.createdStaleAfter] ago;
 * - an abandoned node which was executing is put in error: its executor may have had side effects,
 *   which running it again would repeat;
 * - an abandoned node which had not started executing is dispatched again, unless its instance is too
 *   old, or nobody knows who launched it.
 *
 * A node claimed by a version of Yontrack which did not record its owner is taken as abandoned only
 * once the recovering process has been up for `staleAfter`: by then, the process which claimed it
 * during a rolling upgrade is gone.
 */
object WorkflowNodeRecoveryPolicy {

    const val INTERRUPTED = "Interrupted: the Yontrack process running this node stopped."

    const val TOO_OLD =
        "Abandoned: the Yontrack process handling this node stopped, and the workflow is too old to be resumed."

    const val UNKNOWN_LAUNCHER =
        "Abandoned: the Yontrack process handling this node stopped, and who launched the workflow is not known."

    const val LAUNCHER_GONE =
        "Abandoned: the Yontrack process handling this node stopped, and the account which launched the workflow no longer exists."

    /**
     * @param candidate Unfinished node
     * @param heartbeats Last heartbeat of the known processes
     * @param now Current time
     * @param processStarted Start of the recovering process
     * @param config Recovery settings
     */
    fun decide(
        candidate: WorkflowNodeRecoveryCandidate,
        heartbeats: Map<String, LocalDateTime>,
        now: LocalDateTime,
        processStarted: LocalDateTime,
        config: WorkflowConfigurationProperties.RecoveryConfigProperties,
    ): WorkflowNodeRecoveryAction {
        val abandoned = when (candidate.status) {
            WorkflowInstanceNodeStatus.CREATED -> {
                val dispatched = candidate.dispatched ?: candidate.instanceTimestamp
                dispatched < now - config.createdStaleAfter
            }

            WorkflowInstanceNodeStatus.WAITING,
            WorkflowInstanceNodeStatus.STARTED -> {
                val owner = candidate.owner
                if (owner == null) {
                    processStarted < now - config.staleAfter
                } else {
                    val heartbeat = heartbeats[owner]
                    heartbeat == null || heartbeat < now - config.staleAfter
                }
            }

            else -> false
        }
        return when {
            !abandoned -> WorkflowNodeRecoveryAction.None
            candidate.status == WorkflowInstanceNodeStatus.STARTED -> WorkflowNodeRecoveryAction.Fail(INTERRUPTED)
            candidate.instanceTimestamp < now - config.maxAge -> WorkflowNodeRecoveryAction.Fail(TOO_OLD)
            candidate.launcher == null -> WorkflowNodeRecoveryAction.Fail(UNKNOWN_LAUNCHER)
            else -> WorkflowNodeRecoveryAction.Resume
        }
    }
}
