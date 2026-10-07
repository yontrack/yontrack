package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFilter
import net.nemerosa.ontrack.model.events.EventType
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.Signature
import java.time.LocalDateTime

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

    /**
     * Gets a chunk of the events matching a filter, newest first, with no project restriction,
     * reading the events by keyset rather than by offset.
     *
     * @param filter Criteria on the events
     * @param beforeId Only the events whose ID is lower than this one
     * @param size Maximum number of events to return
     * @param entityLoader Loading of the entities of the events
     * @param eventTypeLoader Loading of the event types
     * @return Events
     */
    fun findEventsBefore(
        filter: EventFilter,
        beforeId: Int,
        size: Int,
        entityLoader: (type: ProjectEntityType, id: ID) -> ProjectEntity,
        eventTypeLoader: (type: String) -> EventType,
    ): List<Event>

    /**
     * Whether more than [offset] events match a filter.
     *
     * @param filter Criteria on the events
     * @param beforeId Only the events whose ID is lower than this one, if any
     * @param offset Number of events to skip
     * @return `true` when there is an event past the [offset] first ones
     */
    fun hasEventsBeyond(filter: EventFilter, beforeId: Int?, offset: Int): Boolean

    /**
     * Deletes, in one statement, at most [size] events posted before [time].
     *
     * @param time Events posted strictly before this time (UTC) are deleted
     * @param size Maximum number of events to delete
     * @return Number of deleted events
     */
    fun deleteEventsBefore(time: LocalDateTime, size: Int): Int

    /**
     * ID of the last event, if any.
     */
    fun getLastEventId(): Int?

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