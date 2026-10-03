package net.nemerosa.ontrack.boot.ui

import net.nemerosa.ontrack.boot.Application
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalManagementPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

/**
 * An evidence storage which cannot be reached — here, a bucket which does not exist in the MinIO
 * of the stack — degrades the health of the instance but never takes it down: the management port
 * answers `DEGRADED` with HTTP 200, so that no probe takes the pods out (#1963).
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.server.port=0",
        "ontrack.extension.audit-trail.storage.bucket=yontrack-no-such-bucket",
    ],
)
class EvidenceStorageDegradedHealthIT : AbstractDSLTestSupport() {

    @LocalManagementPort
    private var managementPort: Int = 0

    private val client: HttpClient = HttpClient.newHttpClient()

    @Test
    fun `Unreachable evidence storage degrades the health with HTTP 200`() {
        val response = client.send(
            HttpRequest.newBuilder(URI("http://localhost:$managementPort/manage/health")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, response.statusCode(), "Health answers 200: ${response.body()}")
        assertEquals("DEGRADED", objectMapper.readTree(response.body()).path("status").asString())
    }
}
