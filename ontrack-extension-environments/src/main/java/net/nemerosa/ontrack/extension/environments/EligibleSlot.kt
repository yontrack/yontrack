package net.nemerosa.ontrack.extension.environments

/**
 * A slot, and whether a given build may enter it.
 *
 * Two questions, asked of every admission rule of the slot:
 *
 * - **eligible** - the build *could* go to this slot, as a candidate;
 * - **deployable** - the build *can go now*: a candidate for it would be runnable at once.
 *
 * A build can be eligible and not yet deployable (not promoted yet, say). Starting a pipeline for it
 * is still allowed - it waits as a candidate - which is why a UI must *say so* rather than refuse.
 *
 * @property slot The slot.
 * @property eligible Whether the build can be a candidate in this slot.
 * @property nonEligibleRules The slot's admission rules which refuse this build - empty whenever
 *   [eligible] is true, since a slot is eligible exactly when none of its rules refuses. It is what
 *   lets a UI say *why* an environment is not on offer instead of showing a disabled row with no
 *   explanation, or hiding the environment altogether and leaving the user to wonder.
 * @property deployable Whether the build is eligible **and** passes every admission rule which can
 *   be decided on the build alone. Rules decided on the pipeline only are not counted against it -
 *   see [pipelineOnlyRules].
 * @property nonDeployableRules The admission rules which prevent an eligible build from being
 *   deployed now, each with its reason. Empty when [deployable] is true, and when the build is not
 *   eligible at all ([nonEligibleRules] explains that).
 * @property pipelineOnlyRules The admission rules which can only be decided once a pipeline exists
 *   for the build, like a manual approval. Not a warning: a note at most. Empty when the build is
 *   not eligible.
 */
data class EligibleSlot(
    val slot: Slot,
    val eligible: Boolean,
    val nonEligibleRules: List<SlotAdmissionRuleConfig> = emptyList(),
    val deployable: Boolean = false,
    val nonDeployableRules: List<EligibleSlotRuleCheck> = emptyList(),
    val pipelineOnlyRules: List<SlotAdmissionRuleConfig> = emptyList(),
)

/**
 * An admission rule of a slot which prevents a build from being deployed now.
 *
 * @property rule The admission rule.
 * @property reason Why the rule refuses the build, as a pipeline would say it.
 */
data class EligibleSlotRuleCheck(
    val rule: SlotAdmissionRuleConfig,
    val reason: String?,
)
