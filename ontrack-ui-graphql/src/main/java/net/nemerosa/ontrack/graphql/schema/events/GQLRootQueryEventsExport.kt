package net.nemerosa.ontrack.graphql.schema.events

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.model.events.EventsExportService
import org.springframework.stereotype.Component

/**
 * What the export of the events matching a filter would hold, so that the UI can warn before
 * downloading a truncated export.
 */
@Component
class GQLRootQueryEventsExport(
    private val gqlTypeEventsExportInfo: GQLTypeEventsExportInfo,
    private val gqlInputEventFilter: GQLInputEventFilter,
    private val eventsExportService: EventsExportService,
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("eventsExport")
            .description(
                "What the export of the events matching a filter would hold: its maximum number " +
                        "of events, and whether more events than that match the filter. " +
                        "Requires the events audit function, granted to the administrators."
            )
            .argument {
                it.name(ARG_FILTER)
                    .description("Filter on the events")
                    .type(gqlInputEventFilter.typeRef)
            }
            .type(gqlTypeEventsExportInfo.typeRef)
            .dataFetcher { env ->
                val filter = gqlInputEventFilter.convert(env.getArgument(ARG_FILTER))
                eventsExportService.exportInfo(filter)
            }
            .build()

    companion object {
        const val ARG_FILTER = "filter"
    }
}
