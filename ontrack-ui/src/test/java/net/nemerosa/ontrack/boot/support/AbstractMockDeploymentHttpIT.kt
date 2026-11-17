package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensConstants
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * The mock deployment API as the `http` channel calls it: over HTTP, through the whole filter chain,
 * Spring Security included.
 *
 * Not transactional, like the CORS tests: the token of the authenticated calls must be committed for
 * the server, on its own threads, to find it.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class AbstractMockDeploymentHttpIT : AbstractDSLTestSupport() {

    @LocalServerPort
    protected var port: Int = 0

    @Autowired
    private lateinit var tokensService: TokensService

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    private val token: String by lazy {
        asAdmin().call {
            tokensService.generateNewToken(TokenOptions(name = uid("mock-"))).value
        }
    }

    /**
     * A call without any authentication, as the `http` channel makes it under an HTTP configuration
     * whose authentication is `none`.
     */
    protected fun anonymous(method: String, path: String, body: String? = null): HttpResponse<String> =
        send(method, path, body, authenticated = false)

    /**
     * A call with an API token.
     */
    protected fun authenticated(method: String, path: String, body: String? = null): HttpResponse<String> =
        send(method, path, body, authenticated = true)

    /**
     * A GraphQL query, with an API token.
     */
    protected fun graphQL(query: String): HttpResponse<String> =
        authenticated(
            "POST",
            "/graphql",
            objectMapper.writeValueAsString(mapOf("query" to query)),
        )

    private fun send(method: String, path: String, body: String?, authenticated: Boolean): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path"))
            .header("Accept", "application/json")
        if (authenticated) {
            builder.header(TokensConstants.HTTP_ONTRACK_TOKEN, token)
        }
        if (body != null) {
            builder.header("Content-Type", "application/json")
            builder.method(method, HttpRequest.BodyPublishers.ofString(body))
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody())
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        const val MOCK_PATH = "/extension/notifications/http/mock"
    }
}
