package net.nemerosa.ontrack.graphql.schema.events

import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.GQLTypeEventType
import net.nemerosa.ontrack.graphql.schema.GQLTypeNameValue
import net.nemerosa.ontrack.graphql.schema.GQLTypeProject
import net.nemerosa.ontrack.graphql.support.GQLScalarLocalDateTime
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.HtmlNotificationEventRenderer
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

/**
 * An event, as read by the audit of the events.
 */
@Component
class GQLTypeEvent(
    private val gqlTypeEventType: GQLTypeEventType,
    private val gqlTypeProject: GQLTypeProject,
    private val gqlTypeEventEntity: GQLTypeEventEntity,
    private val gqlTypeNameValue: GQLTypeNameValue,
    private val eventTemplatingService: EventTemplatingService,
    private val htmlNotificationEventRenderer: HtmlNotificationEventRenderer,
    private val securityService: SecurityService,
) : GQLType {

    override fun getTypeName(): String = TYPE_NAME

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(
                "Record of something that happened to a project entity, or to the instance, " +
                        "with its type, its time, its user, the entities it concerns and its values."
            )
            .field {
                it.name("id")
                    .description("ID of the event")
                    .type(GraphQLNonNull(GraphQLInt))
                    .dataFetcher { env -> env.getSource<Event>()!!.id }
            }
            .field {
                it.name("eventType")
                    .description("Type of the event")
                    .type(GraphQLNonNull(gqlTypeEventType.typeRef))
                    .dataFetcher { env -> env.getSource<Event>()!!.eventType }
            }
            .field {
                it.name("time")
                    .description("Time of the event (UTC)")
                    .type(GraphQLNonNull(GQLScalarLocalDateTime.INSTANCE))
                    .dataFetcher { env -> env.getSource<Event>()!!.signature?.time }
            }
            .field {
                it.name("user")
                    .description("Name of the user who posted the event")
                    .type(GraphQLNonNull(GraphQLString))
                    .dataFetcher { env -> env.getSource<Event>()!!.signature?.user?.name }
            }
            .field {
                it.name("message")
                    .description("Message of the event, rendered as HTML")
                    .type(GraphQLNonNull(GraphQLString))
                    .dataFetcher { env ->
                        val event = env.getSource<Event>()!!
                        // The audit of the events sees all of them, whatever the project ACLs
                        securityService.asAdmin {
                            eventTemplatingService.renderEvent(
                                event = event,
                                context = emptyMap(),
                                template = null,
                                renderer = htmlNotificationEventRenderer,
                            )
                        }
                    }
            }
            .field {
                it.name("project")
                    .description("Project of the event, if any")
                    .type(gqlTypeProject.typeRef)
                    .dataFetcher { env -> env.getSource<Event>()!!.entities[ProjectEntityType.PROJECT] }
            }
            .field {
                it.name("entities")
                    .description("Entities the event is about")
                    .type(listType(gqlTypeEventEntity.typeRef))
                    .dataFetcher { env -> env.getSource<Event>()!!.entities.values.map(EventEntity::of) }
            }
            .field {
                it.name("extraEntities")
                    .description("Additional entities the event is about, like the target of a build link")
                    .type(listType(gqlTypeEventEntity.typeRef))
                    .dataFetcher { env -> env.getSource<Event>()!!.extraEntities.values.map(EventEntity::of) }
            }
            .field {
                it.name("ref")
                    .description("Type of the entity the event refers to first, if any")
                    .type(GraphQLTypeReference(ProjectEntityType::class.java.simpleName))
                    .dataFetcher { env -> env.getSource<Event>()!!.ref }
            }
            .field {
                it.name("values")
                    .description("Values of the event")
                    .type(listType(gqlTypeNameValue.typeRef))
                    .dataFetcher { env -> env.getSource<Event>()!!.values.values.toList() }
            }
            .build()

    companion object {
        const val TYPE_NAME = "Event"
    }
}
