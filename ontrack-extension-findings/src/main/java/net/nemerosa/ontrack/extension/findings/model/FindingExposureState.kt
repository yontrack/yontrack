package net.nemerosa.ontrack.extension.findings.model

/**
 * State of the exposure of a finding on a branch, for the scans of one stamp — or on a branch
 * for all its stamps, see [FindingExposureState.of].
 */
enum class FindingExposureState {

    /**
     * Reported by the latest scan, without any acceptance holding.
     */
    EXPOSED,

    /**
     * Reported by the latest scan, under an acceptance which holds.
     */
    ACCEPTED,

    /**
     * No longer reported by the latest scan.
     */
    RESOLVED;

    companion object {

        /**
         * Rolling up several states — the stamps of one branch, the branches of a project: exposed
         * as soon as one is, else accepted as soon as one is, else resolved.
         *
         * @return The rolled-up state, `null` when there is none to roll up
         */
        fun of(states: Collection<FindingExposureState>): FindingExposureState? = when {
            states.isEmpty() -> null
            EXPOSED in states -> EXPOSED
            ACCEPTED in states -> ACCEPTED
            else -> RESOLVED
        }
    }
}
