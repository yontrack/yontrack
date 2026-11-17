package net.nemerosa.ontrack.extension.notifications.http.auth

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.field
import org.springframework.stereotype.Component

@Component
class GQLTypeHttpAuthenticator : GQLType {
    override fun getTypeName(): String = "HttpAuthenticator"

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Authentication type of an HTTP configuration")
            .field(HttpAuthenticator<*>::type)
            .field(HttpAuthenticator<*>::displayName)
            .build()
}
