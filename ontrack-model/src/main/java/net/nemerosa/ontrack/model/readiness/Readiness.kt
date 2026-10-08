package net.nemerosa.ontrack.model.readiness

import net.nemerosa.ontrack.common.api.APIDescription

/**
 * What a build still lacks to reach a promotion level or a slot.
 *
 * Readiness is computed every time it is read, from the current state of the build and from the
 * current configuration of the promotion level or of the slot. It is read-only: reading it never
 * promotes, deploys or records anything.
 *
 * @property ready Whether nothing is missing
 * @property missing What is missing, empty when [ready] is true
 */
@APIDescription(
    "What a build still lacks to reach a promotion level or a slot, computed now from the current state " +
            "of the build and the current configuration of the target. Reading it changes nothing."
)
data class Readiness(
    @APIDescription(
        "True when nothing is missing: the build already has the promotion level, or it meets every " +
                "condition of the target. A level granted by a person, or a slot with a manual approval, " +
                "keeps it false until that person acts, even when everything else is satisfied."
    )
    val ready: Boolean,
    @APIDescription(
        "Everything the build still lacks, one item per missing condition, empty when `ready` is true. " +
                "All failing conditions are listed, not only the first one."
    )
    val missing: List<ReadinessItem>,
) {
    companion object {
        /**
         * Nothing is missing.
         */
        val READY = Readiness(ready = true, missing = emptyList())

        /**
         * Readiness from a list of missing items: ready when there are none.
         */
        fun of(missing: List<ReadinessItem>) = Readiness(
            ready = missing.isEmpty(),
            missing = missing,
        )
    }
}

/**
 * One thing a build still lacks to reach a promotion level or a slot.
 *
 * @property kind Kind of condition
 * @property name Name of what is missing: a validation stamp, a promotion level, an admission rule...
 * @property message Human (and agent) readable explanation of what is missing
 */
@APIDescription("One thing a build still lacks to reach a promotion level or a slot")
data class ReadinessItem(
    @APIDescription("Kind of the missing condition, which says what can be done about it")
    val kind: ReadinessKind,
    @APIDescription(
        "Name of what is missing: the validation stamp for VALIDATION, the promotion level for PROMOTION, " +
                "the check for CHECK, the admission rule for ADMISSION_RULE, the promotion level or the " +
                "admission rule for MANUAL, the promotion level or the full name of the slot for AGENT_POLICY"
    )
    val name: String,
    @APIDescription("Explanation of what is missing, in plain words, ready to be shown or acted upon")
    val message: String,
)

/**
 * Kind of a missing condition.
 *
 * @property description Description of the kind, for the API
 */
enum class ReadinessKind(
    val description: String,
) {
    VALIDATION(
        "A validation stamp required by the auto promotion of the level has not passed on the build: " +
                "validate it (again) to satisfy it"
    ),
    PROMOTION(
        "A promotion level required by the auto promotion of the level has not been reached by the build"
    ),
    CHECK(
        "A promotion check refuses the promotion, like a previous promotion condition or a promotion " +
                "dependency"
    ),
    ADMISSION_RULE(
        "An admission rule of the slot refuses the build, or does not allow it to be deployed yet"
    ),
    MANUAL(
        "A person must act: the promotion level is not auto promoted and is granted by a person, or the " +
                "slot has a manual approval which has not been given"
    ),
    AGENT_POLICY(
        "The agent reading the readiness is not admitted on the promotion level or on the slot: its owner, " +
                "or a person, must act. Only listed for an agent"
    ),
}
