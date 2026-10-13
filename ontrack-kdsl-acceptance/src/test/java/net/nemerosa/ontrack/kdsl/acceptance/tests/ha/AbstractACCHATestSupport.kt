package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.connector.support.DefaultConnector
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.extension.github.ingestion.GitHubIngestionSettings
import org.apache.commons.codec.digest.HmacAlgorithms
import org.apache.commons.codec.digest.HmacUtils
import tools.jackson.databind.JsonNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*
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

    /**
     * The GitHub ingestion settings, enabled, with the [secret] of the hook. The secret is stored
     * encrypted: a backend checking the signature of a delivery decrypts it.
     */
    protected fun GitHubIngestionSettings.withSecret(secret: String) = GitHubIngestionSettings(
        token = secret,
        retentionDays = retentionDays,
        orgProjectPrefix = orgProjectPrefix,
        indexationInterval = indexationInterval,
        repositoryIncludes = repositoryIncludes,
        repositoryExcludes = repositoryExcludes,
        issueServiceIdentifier = issueServiceIdentifier,
        enabled = true,
    )

    /**
     * Delivers a `ping` event to the GitHub ingestion hook of one backend, as GitHub does, signed
     * with the given secret. Accepted, it is acknowledged and not processed.
     *
     * @param url URL of the backend, [ACCProperties.HA.A] or [ACCProperties.HA.B]
     * @return HTTP status of the delivery: 200 when the backend decrypts the secret the delivery is
     * signed with
     */
    protected fun pingIngestionHook(url: String, signedWith: String): Int {
        val body = """{"zen":"Keep it logically awesome."}"""
        val signature = "sha256=" + HmacUtils(HmacAlgorithms.HMAC_SHA_256, signedWith).hmacHex(body)
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("$url/hook/secured/github/ingestion"))
                .header("Content-Type", "application/json")
                .header("X-GitHub-Delivery", UUID.randomUUID().toString())
                .header("X-GitHub-Event", "ping")
                .header("X-GitHub-Hook-ID", "123456789")
                .header("X-GitHub-Hook-Installation-Target-ID", "1234567890")
                .header("X-GitHub-Hook-Installation-Target-Type", "organization")
                .header("X-Hub-Signature-256", signature)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        return response.statusCode()
    }

}
