package net.nemerosa.ontrack.model.events

import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.Signature
import java.util.*

/**
 * Service used to get access to the list of events.
 */
interface EventQueryService {

    fun getEvents(offset: Int, count: Int): List<Event>

    fun getEvents(entityType: ProjectEntityType, entityId: ID, offset: Int, count: Int): List<Event>

    fun getEvents(entityType: ProjectEntityType, entityId: ID, eventType: EventType, offset: Int, count: Int): List<Event>

    fun getLastEventSignature(entityType: ProjectEntityType, entityId: ID, eventType: EventType): Signature?

    fun getLastEvent(entityType: ProjectEntityType, entityId: ID, eventType: EventType): Event?

    fun getLastEvent(entity: ProjectEntity, eventType: EventType): Event?

    /**
     * Audit view of all the events of the instance, newest first, whatever the project ACLs.
     *
     * Requires the [EventsAudit][net.nemerosa.ontrack.model.security.EventsAudit] global function.
     *
     * There is no total count: the [page info][PaginatedList.pageInfo] only tells whether there is a
     * next page, and its `totalSize` is the number of events known so far.
     *
     * @param filter Criteria on the events
     * @param offset Number of events to skip
     * @param size Maximum number of events to return, capped at [MAX_EVENTS_PAGE_SIZE]
     * @return Page of events
     */
    fun findEvents(filter: EventFilter, offset: Int, size: Int): PaginatedList<Event>

    companion object {
        /**
         * Maximum size of a page of [findEvents] - the entities of each event are loaded one by one.
         */
        const val MAX_EVENTS_PAGE_SIZE = 100
    }

}
