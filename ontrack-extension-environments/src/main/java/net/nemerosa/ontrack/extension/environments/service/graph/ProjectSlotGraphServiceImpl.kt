package net.nemerosa.ontrack.extension.environments.service.graph

import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.model.structure.Project
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ProjectSlotGraphServiceImpl(
    private val slotService: SlotService,
) : ProjectSlotGraphService {

    override fun slotGraph(project: Project, qualifier: String): SlotGraph {
        // Gets the list of all slots for this project and qualifier, in a deterministic order:
        // the set comes from the database without any order, and several environments may share
        // the same order.
        val slots = slotService.findSlotsByProject(project, qualifier).sortedWith(slotOrder)

        // List of nodes
        val slotNodes = slots.map {
            SlotNode(
                slot = it,
                parents = getParents(slots, it),
            )
        }

        // Graph
        return SlotGraph(
            slotNodes = slotNodes,
        )
    }

    /**
     * Gets the parents of a slot, in the order of the given (sorted) slots.
     */
    private fun getParents(slots: List<Slot>, slot: Slot): List<Slot> {
        val lowerSlots = slots.filter { it.environment.order < slot.environment.order }
        return if (lowerSlots.isEmpty()) {
            emptyList()
        } else {
            val immediateLowerOrder = lowerSlots.maxOf { it.environment.order }
            lowerSlots.filter { it.environment.order == immediateLowerOrder }
        }
    }

    companion object {
        /**
         * Environment order first, then the environment name, the way users see the environments.
         * The slot ID is only a safety net: within a project and qualifier, a slot is unique per
         * environment.
         */
        private val slotOrder: Comparator<Slot> = compareBy<Slot>(
            { it.environment.order },
            { it.environment.name },
            { it.id },
        )
    }

}
