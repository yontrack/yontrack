package net.nemerosa.ontrack.boot.ui

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensConstants
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Calls of the demonstration tampering endpoint over HTTP, through the whole stack (#1969).
 */
abstract class DemoTamperingHttpSupport : AbstractDSLTestSupport() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var tokensService: TokensService

    private val client: HttpClient = HttpClient.newHttpClient()

    /**
     * A build with a trail: build.created, then validation.run.
     */
    protected fun trailedBuild(): Build = asAdmin {
        project<Build> {
            branch<Build> {
                val vs = validationStamp()
                build().apply { validate(vs) }
            }
        }
    }

    /**
     * Token of an account with a global role.
     */
    protected fun token(role: String): String =
        // The account is created as an administrator, the token as the account
        asAdmin { asGlobalRole(role) }.call {
            tokensService.generateNewToken(TokenOptions(name = uid("demo-"))).value
        }

    protected fun adminToken(): String = token(Roles.GLOBAL_ADMINISTRATOR)

    protected fun rewritePayload(build: Build, seq: Int, payload: String, token: String): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(
                URI("http://localhost:$port/rest/extension/audit-trail/demo-tampering/builds/${build.id()}/entries/$seq/payload")
            )
                .header(TokensConstants.HTTP_ONTRACK_TOKEN, token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(payload))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
}
