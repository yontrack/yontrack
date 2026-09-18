package net.nemerosa.ontrack.extension.environments.service

import net.nemerosa.ontrack.extension.environments.BuildSlotJourney
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotPipeline
import net.nemerosa.ontrack.model.structure.Build

/**
 * The operational readings of a slot - the three questions the matrix, the slot cell and the slot
 * drawer ask about a slot without caring how it is configured, plus a build's journey through the
 * slots of its project.
 *
 * They live beside [SlotService] rather than in it because each of them is a *composition* of what
 * [SlotService] already answers (the current deployment, its checks, the last deployed one, the
 * eligible builds, the slot graph) into the single word a cell has room for.
 *
 * **The single-slot methods are the readable form; [getSlotStatuses] is the one that scales.** Each
 * of [isBlocked] and [isBehind] re-reads the slot's deployments and, for `behind`, rebuilds the
 * project's slot graph, so a caller asking them of a hundred slots pays a hundred times over. That
 * is the right shape for the drawer and the slot page, which look at one slot. Anything drawing
 * *many* slots - the matrix, the widgets, the project graph - asks [getSlotStatuses] once instead,
 * and the GraphQL fields go through batch loaders so that a query selecting `blocked` on a hundred
 * slots resolves them together (see `SlotStatusDataLoader`).
 */
interface SlotStatusService {

    /**
     * Is this slot's in-flight deployment held up?
     *
     * True when the slot has an unfinished deployment whose checks for the phase it is in do not
     * pass: admission rules and `CANDIDATE` workflows for a candidate, `RUNNING` workflows for a
     * running one. An overridden check passes, so an overridden blocker is not a blocker.
     *
     * False when nothing is in flight - a slot with no deployment is idle, not blocked.
     */
    fun isBlocked(slot: Slot): Boolean

    /**
     * Does a slot upstream of this one hold a newer build?
     *
     * Upstream means the slot's parents in the project's slot graph: the slots of the same project
     * and qualifier sitting at the immediately lower environment order. "Newer" compares build ids,
     * which is the order builds are created in.
     *
     * A slot which has never been deployed is behind as soon as a parent holds anything at all.
     */
    fun isBehind(slot: Slot): Boolean

    /**
     * The builds this slot could move to next: the eligible, deployable builds newer than the one
     * it holds - the in-flight one if there is one, otherwise the last deployed one.
     *
     * @param slot Slot to look at
     * @param count Maximum number of builds to return, newest first
     */
    fun getNextBuilds(slot: Slot, count: Int = 3): List<Build>

    /**
     * Where this build stands in every slot of its project, in environment order.
     */
    fun getBuildJourney(build: Build): List<BuildSlotJourney>

    /**
     * Everything a slot cell draws, for many slots at once.
     *
     * This is the method the matrix is built on, and the one the GraphQL batch loaders call. What it
     * costs, and what it deliberately still costs:
     *
     * * **one** query for the slots of every project involved - not of every slot asked for, because
     *   `behind` reads the whole project-and-qualifier graph and a slot hidden by a column filter is
     *   still upstream of a visible one;
     * * **one** query for the current deployments and **one** for the last deployed ones, over that
     *   whole set;
     * * `blocked` then costs a check *per deployment still in flight*. That is not a fan-out over
     *   slots: an idle slot is never blocked and is never looked at. A matrix of a hundred slots
     *   with three deployments on the way runs three checks, and a site where a hundred deployments
     *   are genuinely in flight at once is paying for a hundred real deployments.
     *
     * @param slots Slots to read. Ones the current user cannot see are left out of the answer rather
     *   than answered with defaults.
     * @return The readings by slot id.
     */
    fun getSlotStatuses(slots: Collection<Slot>): Map<String, SlotStatus>

}

/**
 * What [SlotStatusService.getSlotStatuses] answers about one slot: everything a slot cell draws.
 *
 * @property slot The slot itself
 * @property currentPipeline Its most recent deployment, whatever became of it - null when it has
 *   never had one. *Not* necessarily in flight: `finished` says which.
 * @property lastDeployedPipeline The last deployment which actually completed - what the slot is
 *   holding - or null when nothing ever reached it.
 * @property blocked See [SlotStatusService.isBlocked]
 * @property behind See [SlotStatusService.isBehind]
 */
data class SlotStatus(
    val slot: Slot,
    val currentPipeline: SlotPipeline?,
    val lastDeployedPipeline: SlotPipeline?,
    val blocked: Boolean,
    val behind: Boolean,
)
