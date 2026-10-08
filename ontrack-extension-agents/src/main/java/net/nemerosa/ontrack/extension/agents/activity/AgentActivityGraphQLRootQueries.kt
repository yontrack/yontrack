package net.nemerosa.ontrack.extension.agents.activity

import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.events.GQLTypeEvent
import net.nemerosa.ontrack.graphql.support.GraphQLBeanConverter
import net.nemerosa.ontrack.graphql.support.dateTimeArgument
import net.nemerosa.ontrack.graphql.support.intArgument
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.graphql.support.stringArgument
import net.nemerosa.ontrack.graphql.support.stringListArgument
import net.nemerosa.ontrack.model.annotations.getAPITypeName
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventQueryService
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * `AgentActivityStats` - the counts of the agent activity widget.
 */
@Component
class GQLTypeAgentActivityStats : GQLType {

    override fun getTypeName(): String = getAPITypeName(AgentActivityStats::class)

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLBeanConverter.asObjectType(AgentActivityStats::class, cache)
}

/**
 * `agentActivityStats(window, projects, labels)` - how much of the delivery the agents drive, across
 * the projects the caller can see (#2035).
 */
@Component
class GQLRootQueryAgentActivityStats(
    private val gqlTypeAgentActivityStats: GQLTypeAgentActivityStats,
    private val agentActivityStatsService: AgentActivityStatsService,
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("agentActivityStats")
            .description(
                "How much of the delivery the agents drive over a window of days: builds, promotions and " +
                        "deployments by agents, and the assisted share of the builds - " +
                        "across the projects the caller can see. " +
                        "Requires the Agent governance licence (`extension.agents`)."
            )
            .argument(
                intArgument(
                    ARG_WINDOW,
                    "Number of days of the window, ending now - ${AgentActivityStatsFilter.DEFAULT_WINDOW} by default, at most ${AgentActivityStatsFilter.MAX_WINDOW}",
                )
            )
            .argument(
                stringListArgument(
                    ARG_PROJECTS,
                    "Names of the projects to narrow the counts to - all the visible projects when empty or absent",
                    nullable = true,
                )
            )
            .argument(
                stringListArgument(
                    ARG_LABELS,
                    "Labels the projects must all carry, as `category:name` (`name` for a label without a category) - no restriction when empty or absent",
                    nullable = true,
                )
            )
            .type(GraphQLTypeReference(gqlTypeAgentActivityStats.typeName))
            .dataFetcher { env ->
                agentActivityStatsService.getAgentActivityStats(
                    AgentActivityStatsFilter(
                        window = env.getArgument<Int>(ARG_WINDOW) ?: AgentActivityStatsFilter.DEFAULT_WINDOW,
                        projects = env.getArgument<List<*>>(ARG_PROJECTS)?.map { it.toString() } ?: emptyList(),
                        labels = env.getArgument<List<*>>(ARG_LABELS)?.map { it.toString() } ?: emptyList(),
                    )
                )
            }
            .build()

    companion object {
        private const val ARG_WINDOW = "window"
        private const val ARG_PROJECTS = "projects"
        private const val ARG_LABELS = "labels"
    }
}

/**
 * `agentActions(...)` - the latest actions of the agents, on the projects the caller can see (#2035).
 */
@Component
class GQLRootQueryAgentActions(
    private val paginatedListFactory: GQLPaginatedListFactory,
    private val agentActionsService: AgentActionsService,
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        paginatedListFactory.createRootPaginatedField<Event>(
            cache = GQLTypeCache(),
            fieldName = "agentActions",
            fieldDescription = "The latest actions of the agents: the events whose actor is an agent, newest first, " +
                    "on the projects the caller can see (the events without a project for the administrators only). " +
                    "A page holds at most ${EventQueryService.MAX_EVENTS_PAGE_SIZE} events. " +
                    "There is no total count: `pageInfo.nextPage` tells whether there are more events. " +
                    "The events follow the retention of the events. " +
                    "Requires the Agent governance licence (`extension.agents`).",
            itemType = GQLTypeEvent.TYPE_NAME,
            itemTypeSuffix = "AgentActions",
            arguments = listOf(
                stringArgument(ARG_AGENT, "Identifier of one agent, `<slug>[agent]` - all the agents when absent"),
                dateTimeArgument(ARG_FROM, "Events at or after this time (UTC, inclusive)"),
                dateTimeArgument(ARG_TO, "Events at or before this time (UTC, inclusive)"),
                stringListArgument(
                    ARG_EVENT_TYPES,
                    "IDs of the event types to keep - empty or absent for all the types",
                    nullable = true,
                ),
                stringArgument(
                    ARG_PROJECT,
                    "Name of a project, matching the event's project or its extra project",
                ),
            ),
            itemPaginatedListProvider = { env, offset, size ->
                agentActionsService.findAgentActions(
                    filter = AgentActionsFilter(
                        agent = env.getArgument<String>(ARG_AGENT)?.takeIf { it.isNotBlank() },
                        from = env.getArgument<LocalDateTime>(ARG_FROM),
                        to = env.getArgument<LocalDateTime>(ARG_TO),
                        eventTypes = env.getArgument<List<*>>(ARG_EVENT_TYPES)?.map { it.toString() },
                        project = env.getArgument<String>(ARG_PROJECT)?.takeIf { it.isNotBlank() },
                    ),
                    offset = offset,
                    size = size,
                )
            },
        )

    companion object {
        private const val ARG_AGENT = "agent"
        private const val ARG_FROM = "from"
        private const val ARG_TO = "to"
        private const val ARG_EVENT_TYPES = "eventTypes"
        private const val ARG_PROJECT = "project"
    }
}
