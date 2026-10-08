package net.nemerosa.ontrack.extension.agents.activity

import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.structure.ID

/**
 * What the agents did, read from the event log: the events whose actor is an agent. There is no other
 * record of them, and they follow the retention of the events.
 *
 * Under the licence of the agent governance (`extension.agents`): without it, every read is refused.
 */
interface AgentActionsService {

    /**
     * The agent actions, newest first, restricted to what the current user may see.
     *
     * * Only the events whose actor is an agent - [AgentActionsFilter.agent] narrows them to one agent.
     * * Only the events concerning projects the current user can see, both as their project and as
     *   their extra project - a link from a hidden project is left out.
     * * The events without a project are visible to the administrators only, holders of the events
     *   audit function.
     *
     * Unlike the audit of the events, this read needs no particular right: anybody reads the actions
     * on the projects they can see.
     *
     * There is no total count: the [page info][PaginatedList.pageInfo] only tells whether there is a
     * next page.
     *
     * @param filter Criteria on the actions
     * @param offset Number of events to skip
     * @param size Maximum number of events to return, capped at
     * [MAX_EVENTS_PAGE_SIZE][net.nemerosa.ontrack.model.events.EventQueryService.MAX_EVENTS_PAGE_SIZE]
     * @return Page of events
     * @throws net.nemerosa.ontrack.extension.license.control.LicenseFeatureException When the licence
     * does not allow the agent governance
     */
    fun findAgentActions(filter: AgentActionsFilter, offset: Int, size: Int): PaginatedList<Event>

    /**
     * The activity of one agent: its actions, as [findAgentActions] reads them.
     *
     * Only the owner of the agent and the administrators read it - anybody else is refused, even if
     * they can see the projects the agent acted on.
     *
     * @param agentId ID of the agent
     * @param filter Criteria on the actions
     * @param offset Number of events to skip
     * @param size Maximum number of events to return, capped as for [findAgentActions]
     * @return Page of events
     * @throws net.nemerosa.ontrack.extension.license.control.LicenseFeatureException When the licence
     * does not allow the agent governance
     * @throws org.springframework.security.access.AccessDeniedException When the current user is neither
     * the owner of the agent nor an administrator, or when there is no such agent
     */
    fun getAgentActivity(agentId: ID, filter: AgentActivityFilter, offset: Int, size: Int): PaginatedList<Event>
}
