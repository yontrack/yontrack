package net.nemerosa.ontrack.extension.environments.ui

import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.service.SlotStatus
import net.nemerosa.ontrack.extension.environments.service.SlotStatusService
import org.springframework.graphql.execution.BatchLoaderRegistry
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * The batching behind `Slot.blocked`, `Slot.behind`, `Slot.currentPipeline` and
 * `Slot.lastDeployedPipeline`.
 *
 * Those four fields are what a slot cell draws, and the matrix draws one cell per project per
 * environment: a page of twenty projects across five environments selects them a hundred times in
 * one query. Resolved one source at a time, that is a hundred current-deployment queries, a hundred
 * last-deployed ones and a hundred slot-graph rebuilds. Through this loader it is one call to
 * [SlotStatusService.getSlotStatuses] per level of the query, which reads them all together.
 *
 * The keys are the slots themselves rather than their ids, because the batch needs the project and
 * the qualifier of each anyway and re-reading them would be the very query being avoided. [Slot] is
 * a data class, so it keys a map by value.
 *
 * Registered as a **mapped** batch loader, and every key is answered: a key missing from a mapped
 * loader's answer resolves to `null`, and `blocked` and `behind` are non-null fields. A slot the
 * caller cannot see is answered with the idle reading rather than left out.
 */
@Component
class SlotStatusDataLoader(
    registry: BatchLoaderRegistry,
    private val slotStatusService: SlotStatusService,
) {

    init {
        registry.forName<Slot, SlotStatus>(NAME)
            /*
             * `Mono.fromCallable` and no scheduler, as for the workflow instances loader: the lookup
             * is blocking and staying on the GraphQL execution thread is what keeps the caller's
             * security context - which every slot reading is filtered by - the one it runs under.
             */
            .registerMappedBatchLoader { keys, _ -> Mono.fromCallable { load(keys) } }
    }

    /**
     * The readings of each of the given [slots], every key present in the answer.
     */
    fun load(slots: Set<Slot>): Map<Slot, SlotStatus> {
        val found = slotStatusService.getSlotStatuses(slots)
        return slots.associateWith { slot ->
            found[slot.id] ?: SlotStatus(
                slot = slot,
                currentPipeline = null,
                lastDeployedPipeline = null,
                blocked = false,
                behind = false,
            )
        }
    }

    companion object {
        /**
         * The name the slot's data fetchers ask for the loader under.
         */
        const val NAME = "slotStatus"
    }

}
