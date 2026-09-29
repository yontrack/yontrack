package net.nemerosa.ontrack.extension.notifications.subscriptions

import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.*
import net.nemerosa.ontrack.model.annotations.getAPITypeName
import net.nemerosa.ontrack.model.annotations.getPropertyDescription
import org.springframework.stereotype.Component

@Component
class GQLTypeEventSubscriptionPayload : GQLType {
    override fun getTypeName(): String = EventSubscriptionPayload::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType = GraphQLObjectType.newObject()
        .name(typeName)
        .description(getAPITypeName(EventSubscriptionPayload::class))
        .field {
            it.name("id")
                .description("Name of the subscription")
                .deprecate("Removed in V6. Use name instead. See #1927")
                .type(GraphQLString)
                .dataFetcher { env ->
                    env.getSource<EventSubscriptionPayload>()!!.name
                }
        }
        .stringField(EventSubscriptionPayload::name)
        .stringField(EventSubscriptionPayload::channel)
        .field {
            it.name(EventSubscriptionPayload::channelConfig.name)
                .description(getPropertyDescription(EventSubscriptionPayload::channelConfig))
                .type(GQLScalarJSON.INSTANCE.toNotNull())
        }
        .field {
            it.name(EventSubscriptionPayload::events.name)
                .description(getPropertyDescription(EventSubscriptionPayload::events))
                .type(listType(GraphQLString))
        }
        .stringField(EventSubscriptionPayload::keywords)
        .booleanField(EventSubscriptionPayload::disabled)
        .stringField(EventSubscriptionPayload::contentTemplate)
        .build()

}