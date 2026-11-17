package net.nemerosa.ontrack.extension.notifications.http.mock

import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import net.nemerosa.ontrack.extension.notifications.NotificationsConfigProperties
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Where the backend reaches its own [mock deployment API][MockDeploymentController], for an HTTP
 * configuration calling it from this very instance - the demo's.
 *
 * The URL of the instance a client knows is not it: on a deployed instance, it is an ingress routing
 * `/graphql` and `/hook` to the backend and everything else to the UI. The backend reaches itself on
 * the loopback, on the port it is listening on.
 *
 * Like the mock deployment API, the query exists only where
 * [NotificationsConfigProperties.HTTP_MOCK_ENABLED] is set, which is how a client knows it is enabled.
 */
@Component
@ConditionalOnProperty(
    name = [NotificationsConfigProperties.HTTP_MOCK_ENABLED],
    havingValue = "true",
    matchIfMissing = false
)
class GQLRootQueryHttpMockDeploymentsUrl(
    private val environment: Environment,
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("httpMockDeploymentsUrl")
            .description(
                "Base URL under which the backend reaches its own mock deployment API, to use in an " +
                        "HTTP configuration. Only where the mock deployment API is enabled."
            )
            .type(GraphQLNonNull(GraphQLString))
            .dataFetcher { mockDeploymentsBaseUrl(environment) }
            .build()
}

/**
 * Base URL of the mock deployment API, as the backend reaches itself: on the loopback, on the port it
 * is listening on (`local.server.port`, known once it has started, else `server.port`), under its
 * context path.
 */
fun mockDeploymentsBaseUrl(environment: Environment): String {
    val scheme = if (environment.getProperty("server.ssl.enabled", Boolean::class.java, false)) "https" else "http"
    val port = environment.getProperty("local.server.port")?.takeIf { it.isNotBlank() }
        ?: environment.getProperty("server.port")?.takeIf { it.isNotBlank() }
        ?: "8080"
    val contextPath = environment.getProperty("server.servlet.context-path").orEmpty().trimEnd('/')
    return "$scheme://localhost:$port$contextPath${MockDeploymentController.PATH}"
}
