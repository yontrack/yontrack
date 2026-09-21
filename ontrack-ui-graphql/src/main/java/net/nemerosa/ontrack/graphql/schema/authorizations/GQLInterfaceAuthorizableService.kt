package net.nemerosa.ontrack.graphql.schema.authorizations

import graphql.schema.DataFetcher
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.graphql.schema.GQLInterfaceAuthorizable
import net.nemerosa.ontrack.model.security.Authorization
import net.nemerosa.ontrack.model.security.AuthorizationService
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class GQLInterfaceAuthorizableService(
    private val authorizationService: AuthorizationService,
) {

    fun <X : Any, T : Any> apply(
        builder: GraphQLObjectType.Builder,
        kClass: KClass<T>,
        contextConverter: (X) -> T,
    ) {
        apply(builder) { env ->
            // Gets the current context
            val context = env.getSource<X>()!!
            val data = contextConverter(context)
            // Gets the authorizations for this context
            authorizationService.getAuthorizations(data)
        }
    }

    fun <T : Any> apply(
        builder: GraphQLObjectType.Builder,
        kClass: KClass<T>
    ) {
        apply(builder) { env ->
            // Gets the current context
            val context = env.getSource<T>()!!
            // Gets the authorizations for this context
            authorizationService.getAuthorizations(context)
        }
    }

    private fun apply(
        builder: GraphQLObjectType.Builder,
        dataFetcher: DataFetcher<List<Authorization>>,
    ) {
        // Applies the interface
        builder.withInterface(GraphQLTypeReference(GQLInterfaceAuthorizable.NAME))
        // Applies the `authorizations` field
        builder.field(
            GQLInterfaceAuthorizable.authorizationFieldBuilder()
                .dataFetcher(dataFetcher)
                .build()
        )
    }
}