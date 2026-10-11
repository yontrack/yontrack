package net.nemerosa.ontrack.extension.workflows.engine

import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.model.security.AuthenticationStorageService

/**
 * Who launched a workflow instance, so that its nodes can be dispatched again under the same
 * identity when they are recovered.
 *
 * @property account Email of the account, or null when the system launched the instance
 * @property actor Actor of the launch
 */
data class WorkflowInstanceLauncher(
    val account: String?,
    val actor: Actor?,
) {
    companion object {

        /**
         * Launcher for the current authentication, or null if it cannot be recorded.
         */
        fun current(authenticationStorageService: AuthenticationStorageService): WorkflowInstanceLauncher? =
            try {
                val accountId = authenticationStorageService.getAccountId()
                WorkflowInstanceLauncher(
                    // The run-as administrator is no account: it is recovered as the system
                    account = accountId.takeIf { it != AuthenticationStorageService.RUN_AS_ADMINISTRATOR_ACCOUNT_ID },
                    actor = authenticationStorageService.getActor(),
                )
            } catch (_: Exception) {
                // No authentication, or one which cannot be stored
                null
            }
    }
}
