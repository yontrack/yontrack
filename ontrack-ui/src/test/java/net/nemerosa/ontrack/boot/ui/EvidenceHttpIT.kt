package net.nemerosa.ontrack.boot.ui

import net.nemerosa.ontrack.boot.Application
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.security.ValidationRunCreate
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensConstants
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Upload and download of the evidences over HTTP, through the whole stack — security filters,
 * multipart limits and lazy resolution, response headers — against the MinIO of the integration
 * test stack (#1964).
 *
 * Not transactional: the server answers on its own threads, and must find what the test created.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EvidenceHttpIT : AbstractDSLTestSupport() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var tokensService: TokensService

    private val client: HttpClient = HttpClient.newHttpClient()

    private fun validationRun(): ValidationRun = asAdmin {
        project<ValidationRun> {
            branch<ValidationRun> {
                val vs = validationStamp()
                build().validate(vs)
            }
        }
    }

    /**
     * Token of a user who can create validation runs on the project of the run, as CI does.
     */
    private fun ValidationRun.creatorToken(): String =
        asUserWithView(this).withProjectFunction(this, ValidationRunCreate::class.java).call {
            tokensService.generateNewToken(TokenOptions(name = uid("ci-"))).value
        }

    /**
     * Token of a user who can only see the validation run.
     */
    private fun ValidationRun.viewerToken(): String =
        asUserWithView(this).call {
            tokensService.generateNewToken(TokenOptions(name = uid("viewer-"))).value
        }

    private fun sha256(content: ByteArray): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content))

    private fun upload(
        run: ValidationRun,
        token: String?,
        content: ByteArray,
        fileName: String,
        partType: String,
        fields: Map<String, String> = emptyMap(),
    ): HttpResponse<String> {
        val boundary = "evidence-${UUID.randomUUID()}"
        val body = ByteArrayOutputStream().apply {
            fields.forEach { (name, value) ->
                write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            }
            write(
                ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n" +
                        "Content-Type: $partType\r\n\r\n").toByteArray()
            )
            write(content)
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()
        return client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/rest/extension/audit-trail/validation-runs/${run.id()}/evidence"))
                .apply { if (token != null) header(TokensConstants.HTTP_ONTRACK_TOKEN, token) }
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun download(token: String, path: String): HttpResponse<ByteArray> =
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                .header(TokensConstants.HTTP_ONTRACK_TOKEN, token)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )

    /**
     * A PDF of 2 MB, bigger than the 1 MB per file Spring Boot allows by default.
     */
    private fun bigPdf(): ByteArray =
        "%PDF-1.7\n% ${uid("pdf-")}\n".toByteArray() + ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }

    @Test
    fun `A 2 MB PDF uploaded, then downloaded inline without sniffing`() {
        val run = validationRun()
        val token = run.creatorToken()
        val content = bigPdf()
        val response = upload(run, token, content, "trivy.pdf", "application/pdf")
        assertEquals(201, response.statusCode(), response.body())
        val evidence = response.body().parseAsJson()
        assertEquals(sha256(content), evidence.path("sha256").asString())
        assertEquals(content.size.toLong(), evidence.path("size").asLong())

        val download = download(run.viewerToken(), evidence.path("downloadUrl").asString())
        assertEquals(200, download.statusCode())
        assertTrue(content.contentEquals(download.body()), "Bytes downloaded as uploaded")
        assertEquals("application/pdf", download.headers().firstValue("Content-Type").orElse(null))
        assertEquals(listOf("nosniff"), download.headers().allValues("X-Content-Type-Options"))
        assertEquals(
            "default-src 'none'; style-src 'unsafe-inline'",
            download.headers().firstValue("Content-Security-Policy").orElse(null)
        )
        assertTrue(download.headers().firstValue("Content-Disposition").orElse("").startsWith("inline;"))
    }

    @Test
    fun `HTML downloaded as an octet-stream attachment in a sandbox`() {
        val run = validationRun()
        val content = "<html><script>alert(document.cookie)</script><!-- ${uid("h-")} --></html>".toByteArray()
        val response = upload(run, run.creatorToken(), content, "zap.html", "text/html")
        assertEquals(201, response.statusCode(), response.body())
        val evidence = response.body().parseAsJson()

        val download = download(run.viewerToken(), evidence.path("downloadUrl").asString())
        assertEquals(200, download.statusCode())
        assertEquals("application/octet-stream", download.headers().firstValue("Content-Type").orElse(null))
        assertTrue(download.headers().firstValue("Content-Disposition").orElse("").startsWith("attachment;"))
        assertEquals(listOf("nosniff"), download.headers().allValues("X-Content-Type-Options"))
        assertEquals(
            "default-src 'none'; style-src 'unsafe-inline'; sandbox",
            download.headers().firstValue("Content-Security-Policy").orElse(null)
        )
    }

    @Test
    fun `A refused evidence answers its status and code`() {
        val run = validationRun()
        val response = upload(
            run, run.creatorToken(), bigPdf(), "trivy.pdf", "application/pdf",
            fields = mapOf("externalDigest" to sha256("something else".toByteArray())),
        )
        assertEquals(422, response.statusCode(), response.body())
        assertEquals("audit-trail.evidence.digest-mismatch", response.body().parseAsJson().path("code").asString())
    }

    @Test
    fun `Upload refused to a user who can only see the validation run`() {
        val run = validationRun()
        val response = upload(run, run.viewerToken(), bigPdf(), "trivy.pdf", "application/pdf")
        assertEquals(403, response.statusCode(), response.body())
    }

    @Test
    fun `Upload refused without authentication`() {
        val run = validationRun()
        val response = upload(run, null, bigPdf(), "trivy.pdf", "application/pdf")
        assertEquals(401, response.statusCode())
    }
}
