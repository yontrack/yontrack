package net.nemerosa.ontrack.extension.environments

/**
 * A slot, and whether a given build may enter it.
 *
 * @property slot The slot.
 * @property eligible Whether the build can be a candidate in this slot.
 * @property nonEligibleRules The slot's admission rules which refuse this build - empty whenever
 *   [eligible] is true, since a slot is eligible exactly when none of its rules refuses. It is what
 *   lets a UI say *why* an environment is not on offer instead of showing a disabled row with no
 *   explanation, or hiding the environment altogether and leaving the user to wonder.
 */
data class EligibleSlot(
    val slot: Slot,
    val eligible: Boolean,
    val nonEligibleRules: List<SlotAdmissionRuleConfig> = emptyList(),
)
