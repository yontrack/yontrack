package net.nemerosa.ontrack.extension.agents.activity

import net.nemerosa.ontrack.extension.agents.license.AgentsLicense
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventFilter
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.pagination.PageInfo
import net.nemerosa.ontrack.model.pagination.PageRequest
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.security.AgentService
import net.nemerosa.ontrack.model.security.EventsAudit
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.repository.EventRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AgentActionsServiceImpl(
    private val agentsLicense: AgentsLicense,
    private val agentService: AgentService,
    private val securityService: SecurityService,
    private val structureService: StructureService,
    private val eventFactory: EventFactory,
    private val eventRepository: EventRepository,
) : AgentActionsService {

    override fun findAgentActions(filter: AgentActionsFilter, offset: Int, size: Int): PaginatedList<Event> {
        agentsLicense.checkAgents()
        val actualOffset = offset.coerceAtLeast(0)
        val actualSize = size.coerceIn(1, EventQueryService.MAX_EVENTS_PAGE_SIZE)
        // The administrators see every action, the events without a project included. Anybody else
        // sees the actions on the projects they can see.
        val projects: List<Int>? = if (securityService.isGlobalFunctionGranted(EventsAudit::class.java)) {
            null
        } else {
            structureService.projectList.map { it.id() }
        }
        val eventFilter = EventFilter(
            from = filter.from,
            to = filter.to,
            eventTypes = filter.eventTypes,
            project = filter.project,
            // Only the agents, or one of them
            actor = filter.agent?.takeIf { it.isNotBlank() } ?: EventFilter.ACTOR_AGENT,
        )
        // The events are restricted to the visible projects by the query itself: their entities
        // are loaded without any further check
        val events = securityService.asAdmin {
            eventRepository.findAgentEvents(
                filter = eventFilter,
                projects = projects,
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

    override fun getAgentActivity(
        agentId: ID,
        filter: AgentActivityFilter,
        offset: Int,
        size: Int
    ): PaginatedList<Event> {
        agentsLicense.checkAgents()
        // The owner of the agent, or an administrator
        val agent = agentService.findAgent(agentId)
            ?: throw AccessDeniedException("Only the owner of an agent or an administrator can read its activity.")
        return findAgentActions(filter.forAgent(agent.email), offset, size)
    }
}
