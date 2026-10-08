package net.nemerosa.ontrack.extension.agents.activity

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.graphql.schema.GQLFieldContributor
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.events.GQLTypeEvent
import net.nemerosa.ontrack.graphql.support.dateTimeArgument
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.graphql.support.stringArgument
import net.nemerosa.ontrack.graphql.support.stringListArgument
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.security.Account
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * `Account.agentActivity` - what an agent did, for its owner and the administrators (#2034).
 */
@Component
class AgentActivityGraphQLFieldContributor(
    private val paginatedListFactory: GQLPaginatedListFactory,
    private val agentActionsService: AgentActionsService,
) : GQLFieldContributor {

    override fun getFields(type: Class<*>): List<GraphQLFieldDefinition> =
        if (Account::class.java.isAssignableFrom(type)) {
            listOf(
                paginatedListFactory.createPaginatedField<Account, Event>(
                    cache = GQLTypeCache(),
                    fieldName = "agentActivity",
                    fieldDescription = "For an agent, what it did: its events, newest first, on the projects the caller can see " +
                            "(the events without a project for the administrators only). " +
                            "Only the owner of the agent and the administrators read it; anybody else is refused. " +
                            "Empty for a person. " +
                            "A page holds at most ${EventQueryService.MAX_EVENTS_PAGE_SIZE} events. " +
                            "There is no total count: `pageInfo.nextPage` tells whether there are more events. " +
                            "The events follow the retention of the events. " +
                            "Requires the Agent governance licence (`extension.agents`).",
                    itemType = GQLTypeEvent.TYPE_NAME,
                    itemTypeSuffix = "AgentActivity",
                    arguments = listOf(
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
                    itemPaginatedListProvider = { env, account, offset, size ->
                        getAgentActivity(env, account, offset, size)
                    },
                )
            )
        } else {
            emptyList()
        }

    private fun getAgentActivity(
        env: DataFetchingEnvironment,
        account: Account,
        offset: Int,
        size: Int,
    ): PaginatedList<Event> =
        if (account.isAgent) {
            agentActionsService.getAgentActivity(
                agentId = account.id,
                filter = AgentActivityFilter(
                    from = env.getArgument<LocalDateTime>(ARG_FROM),
                    to = env.getArgument<LocalDateTime>(ARG_TO),
                    eventTypes = env.getArgument<List<*>>(ARG_EVENT_TYPES)?.map { it.toString() },
                    project = env.getArgument<String>(ARG_PROJECT)?.takeIf { it.isNotBlank() },
                ),
                offset = offset,
                size = size,
            )
        } else {
            PaginatedList.empty()
        }

    companion object {
        private const val ARG_FROM = "from"
        private const val ARG_TO = "to"
        private const val ARG_EVENT_TYPES = "eventTypes"
        private const val ARG_PROJECT = "project"
    }
}
