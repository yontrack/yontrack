package net.nemerosa.ontrack.graphql.schema.events

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventQueryService
import org.springframework.stereotype.Component

/**
 * Audit view of all the events of the instance.
 */
@Component
class GQLRootQueryEvents(
    private val gqlPaginatedListFactory: GQLPaginatedListFactory,
    private val gqlTypeEvent: GQLTypeEvent,
    private val gqlInputEventFilter: GQLInputEventFilter,
    private val eventQueryService: EventQueryService,
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        gqlPaginatedListFactory.createRootPaginatedField<Event>(
            cache = GQLTypeCache(),
            fieldName = "events",
            fieldDescription = "Audit of all the events of the instance, newest first, whatever the project ACLs. " +
                    "Requires the events audit function, granted to the administrators. " +
                    "A page holds at most ${EventQueryService.MAX_EVENTS_PAGE_SIZE} events. " +
                    "There is no total count: `pageInfo.nextPage` tells whether there are more events, " +
                    "and `pageInfo.totalSize` is only the number of events known so far.",
            itemType = gqlTypeEvent.typeName,
            arguments = listOf(
                GraphQLArgument.newArgument()
                    .name(ARG_FILTER)
                    .description("Filter on the events")
                    .type(gqlInputEventFilter.typeRef)
                    .build(),
            ),
            itemPaginatedListProvider = { env, offset, size ->
                val filter = gqlInputEventFilter.convert(env.getArgument(ARG_FILTER))
                eventQueryService.findEvents(filter, offset, size)
            }
        )

    companion object {
        const val ARG_FILTER = "filter"
    }
}
