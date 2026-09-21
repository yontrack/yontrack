package net.nemerosa.ontrack.kdsl.acceptance.tests.github.ingestion

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.kdsl.acceptance.tests.github.AbstractACCDSLGitHubTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.waitUntil
import net.nemerosa.ontrack.kdsl.connector.parse
import net.nemerosa.ontrack.kdsl.spec.extension.github.GitHubConfiguration
import net.nemerosa.ontrack.kdsl.spec.extension.github.gitHub
import net.nemerosa.ontrack.kdsl.spec.extension.github.ingestion.gitHubIngestion
import net.nemerosa.ontrack.kdsl.spec.extension.github.ingestion.ingestion
import net.nemerosa.ontrack.kdsl.spec.settings.settings
import org.junit.jupiter.api.BeforeEach
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

abstract class AbstractACCDSLGitHubIngestionTestSupport : AbstractACCDSLGitHubTestSupport() {

    @BeforeEach
    fun ingestionToken() {
        val token = ontrack.connector.token
            ?: error("Missing authentication token")
        val current = ontrack.settings.gitHubIngestion.get()
        ontrack.settings.gitHubIngestion.set(
            current.withToken(token)
        )
    }

    protected fun sendPayloadToHook(
        gitHubConfiguration: GitHubConfiguration,
        event: String,
        payload: JsonNode,
    ): UUID {
        val response = rawConnector().post(
            "/hook/secured/github/ingestion?configuration=${gitHubConfiguration.name}",
            headers = mapOf(
                "Content-Type" to "application/json",
                "X-GitHub-Delivery" to UUID.randomUUID().toString(),
                "X-GitHub-Event" to event,
                "X-GitHub-Hook-ID" to "123456789",
                "X-GitHub-Hook-Installation-Target-ID" to "1234567890",
                "X-GitHub-Hook-Installation-Target-Type" to "organization",
                "X-Hub-Signature-256" to "signature-is-not-checked",
            ),
            body = payload,
        )
        // Payload: response checks
        assertEquals(200, response.statusCode)
        // Extracting the payload processing UUIS
        return response.body.parse<GitHubIngestionHookResponse>().run {
            assertTrue(processing, "Processing has started")
            assertNotNull(uuid, "The payload has been assigned a UUID")
            // Getting the UUID
            uuid
        }
    }

    protected fun waitUntilPayloadIsProcessed(
        payloadUuid: UUID,
    ) {
        waitUntil {
            val processedPayload = ontrack.gitHub.ingestion.payloads(
                uuid = payloadUuid.toString()
            ).items.firstOrNull()
            if (processedPayload != null) {
                // Completed
                // Not completed yet
                when (processedPayload.status) {
                    "COMPLETED" -> true
                    "ERRORED" -> fail("Payload was processed but finished with an error: ${processedPayload.message}")
                    else -> false
                }
            } else {
                false // Not available yet
            }
        }
    }

}