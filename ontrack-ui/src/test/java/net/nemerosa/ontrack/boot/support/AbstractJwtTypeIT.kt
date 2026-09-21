package net.nemerosa.ontrack.boot.support

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals

/**
 * Which JWT `typ` headers the API accepts, as a client sees it: a call with a bearer token, answered
 * `200` when the token is accepted and `401` when it is not.
 *
 * The tokens are signed with a key generated for the test, which the resource server reads through
 * `spring.security.oauth2.resourceserver.jwt.public-key-location`.
 *
 * Not transactional: the server answers on its own threads.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class AbstractJwtTypeIT : AbstractDSLTestSupport() {

    @LocalServerPort
    private var port: Int = 0

    private val client: HttpClient = HttpClient.newHttpClient()

    /**
     * Status of an authenticated call whose token carries the given `typ` header, none if null.
     */
    protected fun callWithTyp(typ: String?): Int {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .apply { if (typ != null) type(JOSEObjectType(typ)) }
            .build()
        val claims = JWTClaimsSet.Builder()
            // Matches the blanked issuer, which Spring Boot still validates
            .issuer("")
            .subject("jwt-typ")
            .claim("email", "jwt-typ@yontrack.test")
            .claim("name", "JWT typ")
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .build()
        val jwt = SignedJWT(header, claims).apply { sign(RSASSASigner(key)) }.serialize()
        return client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/rest/info"))
                .header("Authorization", "Bearer $jwt")
                .header("Accept", "application/json")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        ).statusCode()
    }

    protected fun assertAccepted(typ: String?) {
        assertEquals(200, callWithTyp(typ), "A token with typ=$typ is accepted")
    }

    protected fun assertRejected(typ: String?) {
        assertEquals(401, callWithTyp(typ), "A token with typ=$typ is rejected")
    }

    companion object {

        private val key: RSAKey = RSAKeyGenerator(2048).generate()

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            val pem = "-----BEGIN PUBLIC KEY-----\n" +
                    Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(key.toRSAPublicKey().encoded) +
                    "\n-----END PUBLIC KEY-----\n"
            val file = Files.createTempFile("jwt-typ-", ".pem").toFile().apply {
                deleteOnExit()
                writeText(pem)
            }
            // Blanks the issuer of application.yml, whose decoder would otherwise take precedence
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") { "" }
            registry.add("spring.security.oauth2.resourceserver.jwt.public-key-location") { "file:${file.absolutePath}" }
        }
    }
}
