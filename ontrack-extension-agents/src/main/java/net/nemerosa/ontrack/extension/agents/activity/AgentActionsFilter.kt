package net.nemerosa.ontrack.extension.agents.activity

import java.time.LocalDateTime

/**
 * Filter on the agent actions - the events whose actor is an agent. Every criterion is optional, and
 * they are combined.
 *
 * @property agent Identifier of one agent, `<slug>[agent]`, case-insensitive. All the agents when null.
 * @property from Events at or after this time (UTC, inclusive)
 * @property to Events at or before this time (UTC, inclusive)
 * @property eventTypes IDs of the event types to keep - empty or null for all the types
 * @property project Name of a project, matching the event's project or its extra project
 */
data class AgentActionsFilter(
    val agent: String? = null,
    val from: LocalDateTime? = null,
    val to: LocalDateTime? = null,
    val eventTypes: List<String>? = null,
    val project: String? = null,
)

/**
 * Filter on the activity of one agent: an [AgentActionsFilter] without the agent.
 *
 * @property from Events at or after this time (UTC, inclusive)
 * @property to Events at or before this time (UTC, inclusive)
 * @property eventTypes IDs of the event types to keep - empty or null for all the types
 * @property project Name of a project, matching the event's project or its extra project
 */
data class AgentActivityFilter(
    val from: LocalDateTime? = null,
    val to: LocalDateTime? = null,
    val eventTypes: List<String>? = null,
    val project: String? = null,
) {
    /**
     * The filter on the actions of the [agent], given by its identifier.
     */
    fun forAgent(agent: String) = AgentActionsFilter(
        agent = agent,
        from = from,
        to = to,
        eventTypes = eventTypes,
        project = project,
    )
}
