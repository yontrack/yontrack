package net.nemerosa.ontrack.kdsl.spec.extension.notifications.http

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.HttpMockDeploymentsUrlQuery
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.connector.parseOrNull

/**
 * Mock deployment API of an instance, the target of the `http` channel in the tests and in the demo.
 */
class HttpMockMgt(connector: Connector) : Connected(connector) {

    /**
     * Base URL under which the backend reaches its own mock deployment API, for an HTTP configuration
     * calling it from the instance. Asked over GraphQL, which an ingress routes to the backend.
     *
     * @throws net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLClientException When the mock
     * deployment API is not enabled on the instance: the query does not exist there
     */
    val url: String
        get() = graphqlConnector.query(HttpMockDeploymentsUrlQuery())?.httpMockDeploymentsUrl
            ?: error("The instance did not tell where its mock deployment API is")

    /**
     * Starts a deployment.
     *
     * @param outcome `SUCCEEDED` or `FAILED`, the status of the deployment once it is over
     * @param durationSeconds Time the deployment is running for, the default of the mock when absent
     */
    fun createDeployment(outcome: String? = null, durationSeconds: Int? = null): MockDeployment =
        connector.post(
            "$PATH/deployments",
            body = listOfNotNull(
                outcome?.let { "outcome" to it },
                durationSeconds?.let { "durationSeconds" to it },
            ).toMap(),
        ).body.parseOrNull<MockDeployment>()
            ?: error("The mock deployment API did not return any deployment")

    /**
     * Gets a deployment by id.
     */
    fun deployment(id: String): MockDeployment? =
        connector.get("$PATH/deployments/$id").body.parseOrNull<MockDeployment>()

    companion object {
        /**
         * Path of the mock deployment API, under the root URL of the backend.
         */
        const val PATH = "/extension/notifications/http/mock"
    }
}

/**
 * Deployment of the mock deployment API.
 *
 * @property id Id of the deployment
 * @property status `RUNNING`, `SUCCEEDED` or `FAILED`
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MockDeployment(
    val id: String,
    val status: String,
)
