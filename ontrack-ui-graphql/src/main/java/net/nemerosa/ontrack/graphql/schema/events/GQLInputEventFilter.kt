package net.nemerosa.ontrack.graphql.schema.events

import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.graphql.schema.GQLInputType
import net.nemerosa.ontrack.graphql.schema.dateTimeInputField
import net.nemerosa.ontrack.graphql.schema.inputField
import net.nemerosa.ontrack.graphql.schema.stringInputField
import net.nemerosa.ontrack.graphql.support.getTypeDescription
import net.nemerosa.ontrack.model.events.EventFilter
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Input for an [EventFilter].
 */
@Component
class GQLInputEventFilter : GQLInputType<EventFilter> {

    override fun getTypeRef() = GraphQLTypeReference(TYPE_NAME)

    override fun createInputType(dictionary: MutableSet<GraphQLType>): GraphQLInputType =
        GraphQLInputObjectType.newInputObject()
            .name(TYPE_NAME)
            .description(getTypeDescription(EventFilter::class))
            .field(dateTimeInputField(EventFilter::from))
            .field(dateTimeInputField(EventFilter::to))
            .field(stringInputField(EventFilter::user))
            .field(inputField(EventFilter::eventTypes, GraphQLList(GraphQLNonNull(GraphQLString))))
            .field(stringInputField(EventFilter::project))
            .field(stringInputField(EventFilter::actor))
            .build()

    override fun convert(argument: Any?): EventFilter =
        if (argument is Map<*, *>) {
            EventFilter(
                from = argument[EventFilter::from.name] as LocalDateTime?,
                to = argument[EventFilter::to.name] as LocalDateTime?,
                user = argument[EventFilter::user.name] as String?,
                eventTypes = (argument[EventFilter::eventTypes.name] as List<*>?)?.map { it.toString() },
                project = argument[EventFilter::project.name] as String?,
                actor = argument[EventFilter::actor.name] as String?,
            )
        } else {
            EventFilter()
        }

    companion object {
        const val TYPE_NAME = "EventFilterInput"
    }
}
