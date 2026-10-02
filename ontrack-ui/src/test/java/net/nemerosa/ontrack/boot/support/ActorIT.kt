package net.nemerosa.ontrack.boot.support

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import net.nemerosa.ontrack.boot.Application
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.model.security.ActorJwt
import net.nemerosa.ontrack.model.security.ActorVia
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensConstants
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

/**
 * The actor of an API call, as the security context holds it, for each way of authenticating.
 *
 * The actor is read through an end point of this test only. The JWTs are signed with a key
 * generated for the test, which the resource server reads through
 * `spring.security.oauth2.resourceserver.jwt.public-key-location`.
 *
 * Not transactional: the server answers on its own threads.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ActorIT : AbstractDSLTestSupport() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var tokensService: TokensService

    private val client: HttpClient = HttpClient.newHttpClient()

    @Test
    fun `A JWT issued to the UI authenticates the UI`() {
        val email = "${uid("ui-")}@yontrack.test"
        val subject = uid("sub-")
        val actor = actor(jwt(email = email, subject = subject, azp = "ontrack-client"))
        assertEquals(
            Actor(
                account = email,
                via = ActorVia.UI,
                jwt = ActorJwt(iss = ISSUER, sub = subject),
            ),
            actor,
        )
    }

    @Test
    fun `A JWT issued to another client authenticates a JWT`() {
        val email = "${uid("jwt-")}@yontrack.test"
        val subject = uid("sub-")
        val actor = actor(jwt(email = email, subject = subject, azp = "some-script"))
        assertEquals(
            Actor(
                account = email,
                via = ActorVia.JWT,
                jwt = ActorJwt(iss = ISSUER, sub = subject),
            ),
            actor,
        )
    }

    @Test
    fun `A JWT without authorized party authenticates a JWT`() {
        val email = "${uid("jwt-")}@yontrack.test"
        val subject = uid("sub-")
        val actor = actor(jwt(email = email, subject = subject, azp = null))
        assertEquals(
            Actor(
                account = email,
                via = ActorVia.JWT,
                jwt = ActorJwt(iss = ISSUER, sub = subject),
            ),
            actor,
        )
    }

    @Test
    fun `An API token authenticates a token by its name`() {
        val (email, token) = asUser {
            val token = tokensService.generateNewToken(TokenOptions(name = "pipeline"))
            securityService.currentUser?.account?.email to token.value
        }
        val response = call { header(TokensConstants.HTTP_ONTRACK_TOKEN, token) }
        assertEquals(200, response.statusCode())
        assertEquals(
            Actor(account = email!!, via = ActorVia.TOKEN, tokenName = "pipeline"),
            response.body().parseAsJson().parse<Actor>(),
        )
    }

    @Test
    fun `No actor without authentication`() {
        assertEquals(401, call { }.statusCode())
    }

    private fun actor(jwt: String): Actor {
        val response = call { header("Authorization", "Bearer $jwt") }
        assertEquals(200, response.statusCode())
        return response.body().parseAsJson().parse()
    }

    private fun call(auth: HttpRequest.Builder.() -> Unit): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$ACTOR_PATH"))
                .header("Accept", "application/json")
                .apply(auth)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun jwt(email: String, subject: String, azp: String?): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject(subject)
            .claim("email", email)
            .claim("name", "Actor test")
            .apply { if (azp != null) claim("azp", azp) }
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims)
            .apply { sign(RSASSASigner(key)) }
            .serialize()
    }

    /**
     * End point returning the actor of the call, picked up by the component scan of the tests.
     */
    @RestController
    class ActorITController(
        private val securityService: SecurityService,
    ) {
        @GetMapping(ACTOR_PATH)
        fun actor(): Actor? = securityService.currentActor
    }

    companion object {

        private const val ACTOR_PATH = "/rest/test/actor"

        /**
         * Matches the blanked issuer, which Spring Boot still validates
         */
        private const val ISSUER = ""

        private val key: RSAKey = RSAKeyGenerator(2048).generate()

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            val pem = "-----BEGIN PUBLIC KEY-----\n" +
                    Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(key.toRSAPublicKey().encoded) +
                    "\n-----END PUBLIC KEY-----\n"
            val file = Files.createTempFile("actor-", ".pem").toFile().apply {
                deleteOnExit()
                writeText(pem)
            }
            // Blanks the issuer of application.yml, whose decoder would otherwise take precedence
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") { "" }
            registry.add("spring.security.oauth2.resourceserver.jwt.public-key-location") { "file:${file.absolutePath}" }
        }
    }
}
