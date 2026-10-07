package net.nemerosa.ontrack.service.events

import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventFilter
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.events.EventType
import net.nemerosa.ontrack.model.pagination.PageInfo
import net.nemerosa.ontrack.model.pagination.PageRequest
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.security.EventsAudit
import net.nemerosa.ontrack.model.security.ProjectView
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.repository.EventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class EventQueryServiceImpl(
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val eventFactory: EventFactory,
    private val eventRepository: EventRepository,
) : EventQueryService {

    override fun getEvents(offset: Int, count: Int): List<Event> {
        // Gets the list of projects the current user is allowed to view
        val projectIds = structureService.projectList.map { it.id() }
        // Performs the query
        return eventRepository.query(
            projectIds,
            offset,
            count,
            { type, id -> type.getEntityFn(structureService).apply(id) },
            { eventFactory.toEventType(it) }
        )
    }

    override fun getEvents(entityType: ProjectEntityType, entityId: ID, offset: Int, count: Int): List<Event> {
        checkAccess(entityType, entityId)
        return eventRepository.query(
            entityType,
            entityId,
            offset,
            count,
            { type, id -> type.getEntityFn(structureService).apply(id) },
            { eventFactory.toEventType(it) }
        )
    }

    override fun getEvents(
        entityType: ProjectEntityType,
        entityId: ID,
        eventType: EventType,
        offset: Int,
        count: Int,
    ): List<Event> {
        checkAccess(entityType, entityId)
        return eventRepository.query(
            eventType,
            entityType,
            entityId,
            offset,
            count,
            { type, id -> type.getEntityFn(structureService).apply(id) },
            { eventFactory.toEventType(it) }
        )
    }

    override fun getLastEventSignature(
        entityType: ProjectEntityType,
        entityId: ID,
        eventType: EventType,
    ): Signature? {
        checkAccess(entityType, entityId)
        return eventRepository.getLastEventSignature(entityType, entityId, eventType)
    }

    private fun checkAccess(entityType: ProjectEntityType, entityId: ID) {
        securityService.checkProjectFunction(
            structureService.entityLoader().apply(
                entityType, entityId
            ).project,
            ProjectView::class.java
        )
    }

    override fun getLastEvent(entityType: ProjectEntityType, entityId: ID, eventType: EventType): Event? {
        checkAccess(entityType, entityId)
        return eventRepository.getLastEvent(
            entityType, entityId, eventType,
            { type, id -> type.getEntityFn(structureService).apply(id) },
            { eventFactory.toEventType(it) }
        )
    }

    override fun getLastEvent(entity: ProjectEntity, eventType: EventType): Event? =
        getLastEvent(entity.projectEntityType, entity.id, eventType)

    override fun findEvents(filter: EventFilter, offset: Int, size: Int): PaginatedList<Event> {
        securityService.checkGlobalFunction(EventsAudit::class.java)
        val actualOffset = offset.coerceAtLeast(0)
        val actualSize = size.coerceIn(1, EventQueryService.MAX_EVENTS_PAGE_SIZE)
        // The auditor sees all the events, whatever the project ACLs: the entities
        // are loaded without any check on the projects
        val events = securityService.asAdmin {
            eventRepository.findEvents(
                filter = filter,
                offset = actualOffset,
                // One more event, to know if there is a next page
                size = actualSize + 1,
                entityLoader = { type, id -> type.getEntityFn(structureService).apply(id) },
                eventTypeLoader = { eventFactory.toEventType(it) },
            )
        }
        val items = events.take(actualSize)
        val hasNext = events.size > actualSize
        return PaginatedList(
            pageInfo = PageInfo(
                // No total count: only what is known so far
                totalSize = actualOffset + items.size + (if (hasNext) 1 else 0),
                currentOffset = actualOffset,
                currentSize = items.size,
                previousPage = if (actualOffset > 0) {
                    val previousOffset = (actualOffset - actualSize).coerceAtLeast(0)
                    PageRequest(previousOffset, actualOffset - previousOffset)
                } else {
                    null
                },
                nextPage = if (hasNext) PageRequest(actualOffset + items.size, actualSize) else null,
            ),
            pageItems = items,
        )
    }

}
