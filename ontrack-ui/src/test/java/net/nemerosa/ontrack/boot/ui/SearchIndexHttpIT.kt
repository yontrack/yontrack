package net.nemerosa.ontrack.boot.ui

import net.nemerosa.ontrack.boot.Application
import net.nemerosa.ontrack.boot.PROJECT_SEARCH_RESULT_TYPE
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensConstants
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

/**
 * The endpoints which rebuild or reset the search documents, on `POST /rest/search/index/...`, are
 * for those who may launch the rebuild jobs, over HTTP through the whole stack.
 *
 * There is no successful call of the reset here: it deletes the documents of all the types, for
 * whatever else runs against the same database.
 *
 * Not transactional: the server answers on its own threads, and must find the token of the test.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SearchIndexHttpIT : AbstractDSLTestSupport() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var tokensService: TokensService

    private val client: HttpClient = HttpClient.newHttpClient()

    /**
     * Token of an administrator, who holds the application management.
     */
    private fun adminToken(): String =
        asAdmin { asGlobalRole(Roles.GLOBAL_ADMINISTRATOR) }.call {
            tokensService.generateNewToken(TokenOptions(name = uid("admin-"))).value
        }

    /**
     * Token of a user without any role.
     */
    private fun userToken(): String =
        asUser().call {
            tokensService.generateNewToken(TokenOptions(name = uid("user-"))).value
        }

    private fun post(token: String, path: String, body: String = "{}"): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                .header(TokensConstants.HTTP_ONTRACK_TOKEN, token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(Charsets.UTF_8),
        )

    @Test
    fun `Rebuilding the documents of a type is refused without the application management`() {
        val response = post(userToken(), "/rest/search/index/type/$PROJECT_SEARCH_RESULT_TYPE")
        assertEquals(403, response.statusCode(), response.body())
    }

    @Test
    fun `Resetting the documents is refused without the application management`() {
        val response = post(userToken(), "/rest/search/index/reset", """{"reindex": false}""")
        assertEquals(403, response.statusCode(), response.body())
    }

    @Test
    fun `An administrator rebuilds the documents of a type`() {
        val response = post(adminToken(), "/rest/search/index/type/$PROJECT_SEARCH_RESULT_TYPE")
        assertEquals(201, response.statusCode(), response.body())
    }

    @Test
    fun `The search stays open to any authenticated user`() {
        val response = post(
            userToken(),
            "/rest/search",
            """{"token": "${uid("q")}", "type": "$PROJECT_SEARCH_RESULT_TYPE"}""",
        )
        assertEquals(200, response.statusCode(), response.body())
    }

}
