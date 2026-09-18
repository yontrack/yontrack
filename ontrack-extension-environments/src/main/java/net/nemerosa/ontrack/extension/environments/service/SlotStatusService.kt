package net.nemerosa.ontrack.extension.environments.service

import net.nemerosa.ontrack.extension.environments.BuildSlotJourney
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.model.structure.Build

/**
 * The operational readings of a slot - the three questions the matrix, the slot cell and the slot
 * drawer ask about a slot without caring how it is configured, plus a build's journey through the
 * slots of its project.
 *
 * They live beside [SlotService] rather than in it because each of them is a *composition* of what
 * [SlotService] already answers (the current deployment, its checks, the last deployed one, the
 * eligible builds, the slot graph) into the single word a cell has room for.
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

}
