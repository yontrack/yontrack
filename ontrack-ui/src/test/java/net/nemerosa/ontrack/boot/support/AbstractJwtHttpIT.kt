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
import java.net.http.HttpClient
import java.nio.file.Files
import java.time.Instant
import java.util.*

/**
 * Calls to the HTTP server of the test, authenticated with a bearer JWT, as a client of the
 * identity provider sends them.
 *
 * The tokens are signed with a key generated for the test, which the resource server reads through
 * `spring.security.oauth2.resourceserver.jwt.public-key-location`.
 *
 * Not transactional: the server answers on its own threads.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
abstract class AbstractJwtHttpIT : AbstractDSLTestSupport() {

    @LocalServerPort
    protected var port: Int = 0

    protected val client: HttpClient = HttpClient.newHttpClient()

    /**
     * Signed JWT for the given user.
     *
     * @param typ `typ` header of the token, none if null
     * @param email Email claim of the token
     * @param name Name claim of the token
     */
    protected fun jwt(
        typ: String? = "JWT",
        email: String = "jwt-typ@yontrack.test",
        name: String = "JWT typ",
    ): String {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .apply { if (typ != null) type(JOSEObjectType(typ)) }
            .build()
        val claims = JWTClaimsSet.Builder()
            // Matches the blanked issuer, which Spring Boot still validates
            .issuer("")
            .subject(email)
            .claim("email", email)
            .claim("name", name)
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .build()
        return SignedJWT(header, claims).apply { sign(RSASSASigner(key)) }.serialize()
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
