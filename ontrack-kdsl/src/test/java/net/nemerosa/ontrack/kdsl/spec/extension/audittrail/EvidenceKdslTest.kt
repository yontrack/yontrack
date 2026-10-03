package net.nemerosa.ontrack.kdsl.spec.extension.audittrail

import com.sun.net.httpserver.HttpServer
import net.nemerosa.ontrack.kdsl.connector.support.DefaultConnector
import net.nemerosa.ontrack.kdsl.spec.ValidationRun
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * How the KDSL sends an evidence and reports its refusal, against a stub of the API.
 *
 * The KDSL acceptance stack always has its storage: the refusal of an evidence because no storage
 * is configured is covered here.
 */
class EvidenceKdslTest {

    private lateinit var server: HttpServer

    private var requestBody: String = ""
    private var requestContentType: String = ""
    private var responseStatus: Int = 201
    private var responseBody: String = ""

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/rest/extension/audit-trail/validation-runs/12/evidence") { exchange ->
            requestContentType = exchange.requestHeaders.getFirst("Content-Type") ?: ""
            requestBody = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            val bytes = responseBody.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(responseStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach
    fun stop() {
        server.stop(0)
    }

    private val run: ValidationRun
        get() = ValidationRun(
            connector = DefaultConnector(url = "http://localhost:${server.address.port}", token = "token"),
            id = 12u,
            description = null,
            data = null,
            statuses = emptyList(),
        )

    @Test
    fun `An evidence is sent as the file part, with its name, media type, source and digest`() {
        responseBody = """
            {
              "id": 3, "validationRunId": 12, "fileName": "trivy.json", "mediaType": "application/json",
              "size": 2, "sha256": "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
              "collectedAt": "2026-10-03T10:15:30.123", "collectedBy": {"account": "admin"},
              "source": {"tool": "trivy", "version": "0.56.0", "url": "https://ci.example.com/1"},
              "externalDigest": "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
              "deletedAt": null,
              "downloadUrl": "/rest/extension/audit-trail/evidence/3/download"
            }
        """.trimIndent()

        val evidence = run.attachEvidence(
            EvidenceFile(
                fileName = "trivy.json",
                content = "{}".toByteArray(),
                mediaType = "application/json",
                sourceTool = "trivy",
                sourceVersion = "0.56.0",
                sourceUrl = "https://ci.example.com/1",
                externalDigest = "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
            )
        )

        assertTrue(requestContentType.startsWith("multipart/form-data"), requestContentType)
        val parts = multipartParts()
        assertEquals(
            mapOf(
                "file" to "{}",
                "fileName" to "trivy.json",
                "mediaType" to "application/json",
                "sourceTool" to "trivy",
                "sourceVersion" to "0.56.0",
                "sourceUrl" to "https://ci.example.com/1",
                "externalDigest" to "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a",
            ),
            parts,
        )

        assertEquals(3, evidence.id)
        assertEquals(12, evidence.validationRunId)
        assertEquals(2L, evidence.size)
        assertEquals("trivy", evidence.source?.tool)
        assertEquals("/rest/extension/audit-trail/evidence/3/download", evidence.downloadUrl)
    }

    @Test
    fun `An evidence refused because no storage is configured throws with its code`() {
        responseStatus = 503
        responseBody = """
            {
              "status": 503,
              "code": "audit-trail.evidence.storage-not-configured",
              "message": "Audit trail is enabled but no evidence storage is configured: evidence cannot be attached."
            }
        """.trimIndent()

        val ex = assertFailsWith<EvidenceRefusedException> {
            run.attachEvidence(EvidenceFile("tests.txt", "ok".toByteArray(), "text/plain"))
        }

        assertEquals(EvidenceRefusedException.STORAGE_NOT_CONFIGURED, ex.code)
        assertEquals(503, ex.status)
        assertEquals(
            "Audit trail is enabled but no evidence storage is configured: evidence cannot be attached.",
            ex.message,
        )
    }

    /**
     * Parts of the multipart request, by name: the boundary is read from the content type.
     */
    private fun multipartParts(): Map<String, String> {
        val boundary = requestContentType.substringAfter("boundary=").trim('"')
        return requestBody.split("--$boundary")
            .map { it.removePrefix("\r\n") }
            .filter { it.isNotBlank() && it != "--\r\n" && it != "--" }
            .associate { part ->
                val headers = part.substringBefore("\r\n\r\n")
                val body = part.substringAfter("\r\n\r\n").removeSuffix("\r\n")
                val name = Regex("""name="([^"]+)"""").find(headers)?.groupValues?.get(1) ?: error("Part with no name: $part")
                name to body
            }
    }
}
