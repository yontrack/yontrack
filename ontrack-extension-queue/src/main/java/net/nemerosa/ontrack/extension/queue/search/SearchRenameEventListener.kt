package net.nemerosa.ontrack.extension.queue.search

import net.nemerosa.ontrack.extension.queue.dispatching.QueuePostCommitDispatcher
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

/**
 * Queues the re-indexation of the search documents of a renamed project or branch, after the commit
 * of the rename (#2083).
 *
 * Only a rename triggers it, not any update: the update events carry the
 * [previous name][EventFactory.PREVIOUS_NAME] of the project or branch only when they rename it.
 */
@Component
class SearchRenameEventListener(
    private val queuePostCommitDispatcher: QueuePostCommitDispatcher,
    private val searchRenameQueueProcessor: SearchRenameQueueProcessor,
) : EventListener {

    override fun onEvent(event: Event) {
        if (event.values[EventFactory.PREVIOUS_NAME] == null) {
            return
        }
        val type = when (event.eventType) {
            EventFactory.UPDATE_PROJECT -> ProjectEntityType.PROJECT
            EventFactory.UPDATE_BRANCH -> ProjectEntityType.BRANCH
            else -> return
        }
        val entity = event.entities[type] ?: return
        queuePostCommitDispatcher.dispatchAfterCommit(
            searchRenameQueueProcessor,
            SearchRenameQueuePayload(type = type, id = entity.id()),
        )
    }
}
