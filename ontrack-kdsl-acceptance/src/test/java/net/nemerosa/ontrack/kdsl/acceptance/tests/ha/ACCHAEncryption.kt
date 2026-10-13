package net.nemerosa.ontrack.kdsl.acceptance.tests.ha

import net.nemerosa.ontrack.kdsl.acceptance.tests.ACCProperties
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.connector.support.DefaultConnector
import net.nemerosa.ontrack.kdsl.spec.configurations.configurations
import net.nemerosa.ontrack.kdsl.spec.extension.github.ingestion.gitHubIngestion
import net.nemerosa.ontrack.kdsl.spec.extension.jira.JiraConfiguration
import net.nemerosa.ontrack.kdsl.spec.extension.jira.jira
import net.nemerosa.ontrack.kdsl.spec.generateToken
import net.nemerosa.ontrack.kdsl.spec.revokeToken
import net.nemerosa.ontrack.kdsl.spec.settings.settings
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/**
 * One encryption key for all the replicas (#2086), in the `jdbc` store of the stack: a secret
 * encrypted by one backend is decrypted by the other, from the very first one, and after a key is
 * imported through one of them.
 *
 * The secret is the one of the GitHub ingestion hook, which a backend decrypts to check the
 * signature of a delivery.
 *
 * The first of the package to run (`kdslHaTest` orders the classes by [Order]), on the empty
 * database of a stack just started: nothing has encrypted anything yet - the configuration as code
 * of the stack carries no password - and its first test meets a key store without a key. Before
 * #2086, both backends generated one, and each kept encrypting with its own.
 */
@Order(1)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ACCHAEncryption : AbstractACCHATestSupport() {

    @Test
    @Order(1)
    fun `a secret saved through A right after startup is decrypted through B`() {
        // The first secrets, encrypted by both backends at once: each of them needs the key, which
        // the store does not have yet
        val jira = listOf(ontrackA, ontrackB).flatMap { client ->
            (1..FIRST_SECRETS).map { client to uid("ha-key-") }
        }
        val barrier = CyclicBarrier(jira.size)
        val executor = Executors.newFixedThreadPool(jira.size)
        val names = listOf(uid("hook-"), uid("hook-"))
        val (byA, byB) = names.map { ontrackA.generateToken(it) }
        val original = ontrackA.settings.gitHubIngestion.get()
        try {
            jira.map { (client, name) ->
                executor.submit {
                    barrier.await()
                    client.configurations.jira.create(
                        JiraConfiguration(
                            name = name,
                            url = "https://$name.example.com",
                            user = "ha-key",
                            password = "secret-of-$name",
                        )
                    )
                }
            }.forEach { it.get(1, TimeUnit.MINUTES) }

            // Both backends use the same key from then on
            ontrackA.settings.gitHubIngestion.set(original.withSecret(byA))
            assertEquals(200, pingIngestionHook(ACCProperties.HA.B.url, byA), "Secret saved through A decrypted by B")
            ontrackB.settings.gitHubIngestion.set(original.withSecret(byB))
            assertEquals(200, pingIngestionHook(ACCProperties.HA.A.url, byB), "Secret saved through B decrypted by A")
        } finally {
            executor.shutdownNow()
            jira.forEach { (_, name) -> ontrackA.configurations.jira.delete(name) }
            ontrackA.settings.gitHubIngestion.set(original)
            names.forEach { ontrackA.revokeToken(it) }
        }
    }

    @Test
    @Order(2)
    fun `a key imported through A is used by B for its next encryption and decryption`() {
        val names = (1..3).map { uid("hook-") }
        val (before, encryptedByB, encryptedByA) = names.map { ontrackA.generateToken(it) }
        val original = ontrackA.settings.gitHubIngestion.get()
        val originalKey = exportKey()
        try {
            // B has used the current key
            ontrackB.settings.gitHubIngestion.set(original.withSecret(before))
            assertEquals(200, pingIngestionHook(ACCProperties.HA.B.url, before), "Secret decrypted by B before the import")

            importKey(ACCProperties.HA.A.url, newKey())

            // B encrypts with the imported key: A decrypts it
            ontrackB.settings.gitHubIngestion.set(original.withSecret(encryptedByB))
            assertEquals(
                200,
                pingIngestionHook(ACCProperties.HA.A.url, encryptedByB),
                "Secret encrypted by B after the import decrypted by A"
            )

            // A encrypts with the imported key: B decrypts it
            ontrackA.settings.gitHubIngestion.set(original.withSecret(encryptedByA))
            assertEquals(
                200,
                pingIngestionHook(ACCProperties.HA.B.url, encryptedByA),
                "Secret encrypted by A after the import decrypted by B"
            )
        } finally {
            // Back to the original key, the secret encrypted again with it
            importKey(ACCProperties.HA.A.url, originalKey)
            ontrackA.settings.gitHubIngestion.set(original.withSecret(before))
            ontrackA.settings.gitHubIngestion.set(original)
            names.forEach { ontrackA.revokeToken(it) }
        }
    }

    private fun newKey(): String =
        Base64.getEncoder().encodeToString(ByteArray(256).also { SecureRandom().nextBytes(it) })

    private fun exportKey(): String {
        val response = http(
            HttpRequest.newBuilder(URI("${ACCProperties.HA.A.url}/rest/admin/encryption")).GET()
        )
        assertEquals(200, response.statusCode(), "Encryption key exported")
        return response.body()
    }

    private fun importKey(url: String, key: String) {
        val response = http(
            HttpRequest.newBuilder(URI("$url/rest/admin/encryption"))
                .header("Content-Type", "text/plain")
                .PUT(HttpRequest.BodyPublishers.ofString(key))
        )
        assertEquals(202, response.statusCode(), "Encryption key imported through $url")
    }

    private fun http(request: HttpRequest.Builder): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            request.header(DefaultConnector.X_ONTRACK_TOKEN, ACCProperties.Connection.token).build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    companion object {
        /**
         * Number of secrets each backend encrypts at once, first
         */
        private const val FIRST_SECRETS = 4
    }
}
