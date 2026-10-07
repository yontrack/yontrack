package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFilter
import net.nemerosa.ontrack.model.events.EventType
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.Signature

interface EventRepository {

    fun post(event: Event): Event

    fun query(
        allowedProjects: List<Int>,
        offset: Int,
        count: Int,
        entityLoader: (type: ProjectEntityType, id: ID) -> ProjectEntity,
        eventTypeLoader: (type: String) -> EventType,
    ): List<Event>

    fun query(
        entityType: ProjectEntityType,
        entityId: ID,
        offset: Int,
        count: Int,
        entityLoader: (type: ProjectEntityType, id: ID) -> ProjectEntity,
        eventTypeLoader: (type: String) -> EventType,
    ): List<Event>

    fun query(
        eventType: EventType,
        entityType: ProjectEntityType,
        entityId: ID,
        offset: Int,
        count: Int,
        entityLoader: (type: ProjectEntityType, id: ID) -> ProjectEntity,
        eventTypeLoader: (type: String) -> EventType,
    ): List<Event>

    /**
     * Gets the events matching a filter, newest first, with no project restriction.
     *
     * @param filter Criteria on the events
     * @param offset Number of events to skip
     * @param size Maximum number of events to return
     * @param entityLoader Loading of the entities of the events
     * @param eventTypeLoader Loading of the event types
     * @return Events
     */
    fun findEvents(
        filter: EventFilter,
        offset: Int,
        size: Int,
        entityLoader: (type: ProjectEntityType, id: ID) -> ProjectEntity,
        eventTypeLoader: (type: String) -> EventType,
    ): List<Event>

    fun getLastEventSignature(
        entityType: ProjectEntityType,
        entityId: ID,
        eventType: EventType,
    ): Signature?

    fun getLastEvent(
        entityType: ProjectEntityType,
        entityId: ID,
        eventType: EventType,
        entityLoader: (type: ProjectEntityType, id: ID) -> ProjectEntity,
        eventTypeLoader: (type: String) -> EventType,
    ): Event?

}