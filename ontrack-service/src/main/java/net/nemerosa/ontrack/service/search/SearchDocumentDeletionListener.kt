package net.nemerosa.ontrack.service.search

import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.structure.ProjectEntityID
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

/**
 * Deletes the search documents of a deleted branch or build, whatever their type, and those of
 * the builds of a deleted branch. The deletion of a project cascades to its documents in the
 * database; those of a branch or a build do not.
 *
 * The deletion events are posted before the entity is deleted, in the transaction of its
 * deletion: the builds of a deleted branch can still be found.
 */
@Component
class SearchDocumentDeletionListener(
    private val searchDocumentService: SearchDocumentServiceImpl,
) : EventListener {

    override fun onEvent(event: Event) {
        when (event.eventType) {
            EventFactory.DELETE_BRANCH -> searchDocumentService.deleteForEntity(
                ProjectEntityID(ProjectEntityType.BRANCH, event.getIntValue("BRANCH_ID"))
            )

            EventFactory.DELETE_BUILD -> searchDocumentService.deleteForEntity(
                ProjectEntityID(ProjectEntityType.BUILD, event.getIntValue("BUILD_ID"))
            )
        }
    }

}
