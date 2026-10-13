package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.boot.Application
import net.nemerosa.ontrack.it.QueryCounter
import net.nemerosa.ontrack.it.RecordedQueries
import net.nemerosa.ontrack.model.settings.SecuritySettings
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensConstants
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

/**
 * An HTTP request reads each type of settings it needs once, the security settings included,
 * however many times its code asks for them: in one query, fresh from the database, and never
 * again during the request (#2080).
 *
 * The budget is that of a cold cache, before #2080: one query for the security settings, which
 * the authorizations of the caller read twice.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class SettingsPerRequestHttpIT : AbstractJwtHttpIT() {

    @Autowired
    private lateinit var queryCounter: QueryCounter

    @Autowired
    private lateinit var tokensService: TokensService

    private fun graphQL(header: Pair<String, String>) {
        val response = client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/graphql"))
                .header(header.first, header.second)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"query":"{ projects { id name } }"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, response.statusCode(), response.body())
    }

    /**
     * Number of queries of the settings, by type, run by the threads of the HTTP server
     */
    private fun RecordedQueries.settingsQueriesByType(): Map<String, Int> =
        on("SETTINGS")
            .filter { it.thread.startsWith("http-") }
            .groupBy { query -> query.parameters.firstOrNull()?.toString() ?: "?" }
            .mapValues { (_, queries) -> queries.size }

    private fun assertOneQueryPerSettingsType(queries: RecordedQueries) {
        val counts = queries.settingsQueriesByType()
        assertEquals(1, counts[SecuritySettings::class.java.name], "One query for the security settings: $counts")
        assertEquals(emptyMap(), counts.filterValues { it != 1 }, "One query per settings type: $counts")
    }

    @Test
    fun `An authenticated GraphQL request reads the security settings in one query`() {
        val jwt = jwt(email = "${uid("u-")}@yontrack.test", name = "Settings per request")
        // First login, which creates the account
        graphQL("Authorization" to "Bearer $jwt")
        val queries = queryCounter.record {
            graphQL("Authorization" to "Bearer $jwt")
        }
        assertOneQueryPerSettingsType(queries)
    }

    @Test
    fun `An API call by a CI token reads the security settings in one query`() {
        val token = asAdmin().call {
            tokensService.generateNewToken(TokenOptions(name = uid("ci-"))).value
        }
        val queries = queryCounter.record {
            graphQL(TokensConstants.HTTP_ONTRACK_TOKEN to token)
        }
        assertOneQueryPerSettingsType(queries)
    }

}
