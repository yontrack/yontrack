package net.nemerosa.ontrack.extension.notifications.http.auth

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.support.listType
import org.springframework.stereotype.Component

@Component
class GQLRootQueryHttpAuthenticators(
    private val gqlTypeHttpAuthenticator: GQLTypeHttpAuthenticator,
) : GQLRootQuery {
    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("httpAuthenticators")
            .description("List of the authentication types of an HTTP configuration")
            .type(listType(gqlTypeHttpAuthenticator.typeRef))
            .dataFetcher { _ ->
                HttpAuthenticators.authenticators
            }
            .build()

}
