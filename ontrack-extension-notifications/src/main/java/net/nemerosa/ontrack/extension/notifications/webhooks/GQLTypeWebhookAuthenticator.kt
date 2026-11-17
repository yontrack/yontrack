package net.nemerosa.ontrack.extension.notifications.webhooks

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthenticator
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.field
import org.springframework.stereotype.Component

@Component
class GQLTypeWebhookAuthenticator : GQLType {
    override fun getTypeName(): String = "WebhookAuthenticator"

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Webhook authenticator")
            .field(HttpAuthenticator<*>::type)
            .field(HttpAuthenticator<*>::displayName)
            .build()
}
