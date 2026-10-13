package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLClientException
import net.nemerosa.ontrack.kdsl.connector.support.DefaultConnector
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.admin.admin
import net.nemerosa.ontrack.kdsl.spec.extension.github.ingestion.GitHubIngestionSettings
import net.nemerosa.ontrack.kdsl.spec.extension.github.ingestion.gitHubIngestion
import net.nemerosa.ontrack.kdsl.spec.generateToken
import net.nemerosa.ontrack.kdsl.spec.revokeToken
import net.nemerosa.ontrack.kdsl.spec.settings.security
import net.nemerosa.ontrack.kdsl.spec.settings.settings
import org.apache.commons.codec.digest.HmacAlgorithms
import org.apache.commons.codec.digest.HmacUtils
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Settings changed through a replica apply to the next request served by another one: they are
 * read once per request, never cached across requests (#2080).
 *
 * Each test first has B read the settings, which it would have kept in a cache, then changes them
 * through A, and checks that the very next request through B applies them.
 */
class ACCHASettings : AbstractACCHATestSupport() {

    @Test
    fun `a security setting changed through A applies to the next request through B`() {
        val project = ontrackA.createProject(uid("p_"), "")
        val user = ontrackA.admin.createUser(email = "${uid("u-")}@ontrack.local")
        val userOnB = Ontrack(
            connector = DefaultConnector(
                url = ACCProperties.HA.B.url,
                token = ACCProperties.getOrCreateToken(username = user.email),
            )
        )
        ontrackA.settings.security.with(update = { it.withGrantProjectViewToAll(true) }) {
            assertTrue(userOnB.sees(project.name), "Project visible to all through B")

            ontrackA.settings.security.set(
                ontrackA.settings.security.get().withGrantProjectViewToAll(false)
            )

            assertFalse(userOnB.sees(project.name), "Project no longer visible to all through B")
        }
    }

    @Test
    fun `a GitHub ingestion webhook secret rotated through A is enforced by B on the next delivery`() {
        // The secret of the hook is also the API token it acts with: two tokens of the administrator
        val oldName = uid("hook-old-")
        val newName = uid("hook-new-")
        val oldSecret = ontrackA.generateToken(oldName)
        val newSecret = ontrackA.generateToken(newName)
        try {
            ontrackA.settings.gitHubIngestion.with(update = { it.withSecret(oldSecret) }) {
                shareEncryptionKey()
                assertEquals(200, pingB(signedWith = oldSecret), "Delivery signed with the secret accepted by B")

                ontrackA.settings.gitHubIngestion.set(
                    ontrackA.settings.gitHubIngestion.get().withSecret(newSecret)
                )

                assertEquals(400, pingB(signedWith = oldSecret), "Delivery signed with the old secret rejected by B")
                assertEquals(200, pingB(signedWith = newSecret), "Delivery signed with the new secret accepted by B")
            }
        } finally {
            ontrackA.revokeToken(oldName)
            ontrackA.revokeToken(newName)
        }
    }

    /**
     * Whether this client sees the project: a project which is not granted is refused.
     */
    private fun Ontrack.sees(name: String): Boolean =
        try {
            findProjectByName(name) != null
        } catch (e: GraphQLClientException) {
            if (e.message?.contains("Project access not granted") == true) false else throw e
        }

    /**
     * The webhook secret is stored encrypted, with the key of the `jdbc` store. Until #2086, two
     * replicas started together may each generate a key, and one of them then encrypts with a key
     * the other has not: once A has written the secret, the key of the store is imported into both,
     * which reloads it. #2086 removes this step.
     */
    private fun shareEncryptionKey() {
        val export = http(
            HttpRequest.newBuilder(URI("${ACCProperties.HA.A.url}/rest/admin/encryption")).GET()
        )
        assertEquals(200, export.statusCode(), "Encryption key exported")
        val key = export.body()
        listOf(ACCProperties.HA.A.url, ACCProperties.HA.B.url).forEach { url ->
            val import = http(
                HttpRequest.newBuilder(URI("$url/rest/admin/encryption"))
                    .header("Content-Type", "text/plain")
                    .PUT(HttpRequest.BodyPublishers.ofString(key))
            )
            assertEquals(202, import.statusCode(), "Encryption key imported into $url")
        }
    }

    private fun http(request: HttpRequest.Builder): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            request.header(DefaultConnector.X_ONTRACK_TOKEN, ACCProperties.Connection.token).build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun GitHubIngestionSettings.withSecret(secret: String) = GitHubIngestionSettings(
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
     * Delivers a `ping` event to the GitHub ingestion hook of B, as GitHub does, signed with the
     * given secret. Accepted, it is acknowledged and not processed.
     *
     * @return HTTP status of the delivery
     */
    private fun pingB(signedWith: String): Int {
        val body = """{"zen":"Keep it logically awesome."}"""
        val signature = "sha256=" + HmacUtils(HmacAlgorithms.HMAC_SHA_256, signedWith).hmacHex(body)
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("${ACCProperties.HA.B.url}/hook/secured/github/ingestion"))
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
