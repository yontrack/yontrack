package net.nemerosa.ontrack.extension.environments

/**
 * State of a [SlotDeploymentCheck], or of a group of them.
 *
 * Both [PENDING] and [FAILED] block a deployment the same way; they differ in what they say to
 * the user: [PENDING] is waiting for something which is expected to happen (a workflow running,
 * an approval to give), [FAILED] is not going to pass without someone acting on it.
 */
enum class SlotDeploymentCheckState {

    OK,
    PENDING,
    FAILED;

    companion object {

        /**
         * State of a group of checks: [FAILED] as soon as one of them has failed, [PENDING] when
         * none has failed but one is pending, [OK] otherwise - including when there is no check.
         */
        fun of(checks: Collection<SlotDeploymentCheck>): SlotDeploymentCheckState =
            when {
                checks.any { it.state == FAILED } -> FAILED
                checks.any { it.state == PENDING } -> PENDING
                else -> OK
            }
    }
}
