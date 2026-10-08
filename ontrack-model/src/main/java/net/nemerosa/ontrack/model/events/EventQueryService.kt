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

    /**
     * What the agents did on an entity: its events whose actor is an agent, newest first. The
     * entity is the one of the event, or one of its extra entities - for a build, its validations,
     * its promotions, its links...
     *
     * Requires the view of the entity's project. An event touching an entity of a project the user
     * cannot see is left out.
     *
     * There is no total count: the [page info][PaginatedList.pageInfo] only tells whether there is a
     * next page, and a page may hold fewer events than asked for when some were left out.
     *
     * The events are the data source: their retention is the one of the events.
     *
     * @param entity Entity whose agent actions are read
     * @param offset Number of events to skip
     * @param size Maximum number of events to return, capped at [MAX_EVENTS_PAGE_SIZE]
     * @return Page of events
     */
    fun getAgentActions(entity: ProjectEntity, offset: Int, size: Int): PaginatedList<Event>

    companion object {
        /**
         * Maximum size of a page of [findEvents] and [getAgentActions] - the entities of each event are loaded one by one.
         */
        const val MAX_EVENTS_PAGE_SIZE = 100
    }

}
