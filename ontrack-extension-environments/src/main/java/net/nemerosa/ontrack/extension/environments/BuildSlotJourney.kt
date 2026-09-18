package net.nemerosa.ontrack.extension.environments

import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Where one build stands in one slot.
 *
 * The five states are exclusive and ordered by how far the build got: a build which is deployed
 * right now is [DEPLOYED] even though it is also, trivially, eligible; a build which was deployed
 * and has since been replaced is [SUPERSEDED] rather than back to [ELIGIBLE], because "it was here"
 * is the more useful thing to say about it.
 */
enum class BuildSlotJourneyState {

    @APIDescription("The build is the one currently deployed in this slot.")
    DEPLOYED,

    @APIDescription("The build was deployed in this slot and a more recent one has replaced it.")
    SUPERSEDED,

    @APIDescription("The build has an unfinished deployment in this slot (candidate or running).")
    IN_PROGRESS,

    @APIDescription("The build has never been deployed here and every admission rule accepts it.")
    ELIGIBLE,

    @APIDescription("The build has never been deployed here and at least one admission rule refuses it.")
    NOT_ELIGIBLE,
}

/**
 * One step of a build's journey: its state in one slot of its project.
 *
 * @property slot The slot.
 * @property state Where the build stands in it.
 * @property pipeline The build's most recent deployment in this slot, or `null` when it has never
 *   had one - which is exactly the [BuildSlotJourneyState.ELIGIBLE] and
 *   [BuildSlotJourneyState.NOT_ELIGIBLE] cases.
 * @property nonEligibleRules The admission rules refusing this build, empty unless [state] is
 *   [BuildSlotJourneyState.NOT_ELIGIBLE]. It is what lets the UI say *why* in a tooltip instead of
 *   showing a greyed-out chip with no explanation.
 */
data class BuildSlotJourney(
    val slot: Slot,
    val state: BuildSlotJourneyState,
    val pipeline: SlotPipeline?,
    val nonEligibleRules: List<SlotAdmissionRuleConfig> = emptyList(),
)
