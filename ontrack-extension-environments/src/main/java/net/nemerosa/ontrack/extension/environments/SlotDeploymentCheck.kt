package net.nemerosa.ontrack.extension.environments

/**
 * Result of a check for a deployment.
 *
 * A check which is not [ok] blocks the deployment, whether it has [pending] (something is still
 * expected to happen - a workflow running, an approval to give) or has failed. The difference is
 * only in how it is presented: see [state].
 */
data class SlotDeploymentCheck(
    val ok: Boolean,
    val overridden: Boolean,
    val reason: String?,
    val pending: Boolean = false,
) {

    init {
        require(!(ok && pending)) { "A check cannot be both OK and pending" }
    }

    /**
     * State of the check, telling a check still waiting for something from one which has failed.
     */
    val state: SlotDeploymentCheckState
        get() = when {
            ok -> SlotDeploymentCheckState.OK
            pending -> SlotDeploymentCheckState.PENDING
            else -> SlotDeploymentCheckState.FAILED
        }

    companion object {

        fun ok(reason: String? = null) = SlotDeploymentCheck(
            ok = true,
            overridden = false,
            reason = reason,
        )

        fun nok(reason: String?) = SlotDeploymentCheck(
            ok = false,
            overridden = false,
            reason = reason,
        )

        /**
         * A check which does not pass *yet*: it still blocks, but it is not a failure.
         */
        fun pending(reason: String?) = SlotDeploymentCheck(
            ok = false,
            overridden = false,
            reason = reason,
            pending = true,
        )

        fun check(check: Boolean, ok: String, nok: String) = SlotDeploymentCheck(
            ok = check,
            overridden = false,
            reason = if (check) ok else nok,
        )

    }
}
