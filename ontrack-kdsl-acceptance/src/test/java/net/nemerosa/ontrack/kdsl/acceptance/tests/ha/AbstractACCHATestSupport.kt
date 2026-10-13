package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.connector.support.DefaultConnector
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import tools.jackson.databind.JsonNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

/**
 * Cross-replica tests (#2076), run by `kdslHaTest` against `compose/docker-compose-kdsl-ha.yml`:
 * two backends, A and B, behind a round-robin proxy.
 *
 * - [ontrack] goes through the proxy, so that two calls in a row land on two backends
 * - [ontrackA] and [ontrackB] go to one backend each, bypassing the proxy
 *
 * The three share the token, obtained once from A's management endpoint: it lives in the database
 * and works on both backends.
 */
abstract class AbstractACCHATestSupport : AbstractACCDSLTestSupport() {

    /**
     * Backend A, directly
     */
    protected val ontrackA: Ontrack by lazy { direct(ACCProperties.HA.A.url) }

    /**
     * Backend B, directly
     */
    protected val ontrackB: Ontrack by lazy { direct(ACCProperties.HA.B.url) }

    private fun direct(url: String) =
        Ontrack(connector = DefaultConnector(url = url, token = ACCProperties.Connection.token))

    /**
     * Raw GraphQL call to one backend, for what the KDSL does not wrap.
     *
     * @param url URL of the backend, [ACCProperties.HA.A] or [ACCProperties.HA.B]
     * @return The whole response, `data` and `errors`
     */
    protected fun graphQL(url: String, query: String): JsonNode {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("$url/graphql"))
                .header("Content-Type", "application/json")
                .header(DefaultConnector.X_ONTRACK_TOKEN, ACCProperties.Connection.token)
                .POST(HttpRequest.BodyPublishers.ofString(mapOf("query" to query.trimIndent()).asJson().toString()))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, response.statusCode(), "GraphQL call to $url: ${response.body()}")
        return response.body().parseAsJson()
    }

}
