package net.nemerosa.ontrack.extension.audittrail.evidence

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * How an evidence is served: inline only for the allow-listed types whose content agrees with the
 * declared type, as an `application/octet-stream` attachment otherwise.
 */
class EvidenceDispositionTest {

    private val attachment = EvidenceDisposition(inline = false, contentType = "application/octet-stream")

    private fun inline(contentType: String) = EvidenceDisposition(inline = true, contentType = contentType)

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private val pdf = "%PDF-1.7\n%âãÏÓ\n1 0 obj".toByteArray(Charsets.ISO_8859_1)
    private val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D)
    private val jpeg = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46)
    private val gif = "GIF89a\u0001\u0000".toByteArray(Charsets.ISO_8859_1)
    private val webp = "RIFF$\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)
    private val html = "<!DOCTYPE html><html><script>alert(document.cookie)</script></html>".toByteArray()
    private val svg = """<svg xmlns="http://www.w3.org/2000/svg" onload="alert(1)"/>""".toByteArray()

    @Test
    fun `PDF inline when it starts with the PDF header`() {
        assertEquals(inline("application/pdf"), EvidenceDisposition.of("application/pdf", pdf))
    }

    @Test
    fun `Images inline when their magic bytes agree with their type`() {
        assertEquals(inline("image/png"), EvidenceDisposition.of("image/png", png))
        assertEquals(inline("image/jpeg"), EvidenceDisposition.of("image/jpeg", jpeg))
        assertEquals(inline("image/gif"), EvidenceDisposition.of("image/gif", gif))
        assertEquals(inline("image/webp"), EvidenceDisposition.of("image/webp", webp))
    }

    @Test
    fun `GIF87a is a GIF`() {
        assertEquals(
            inline("image/gif"),
            EvidenceDisposition.of("image/gif", "GIF87a....".toByteArray(Charsets.ISO_8859_1))
        )
    }

    @Test
    fun `An image whose magic bytes are another type's is an attachment`() {
        assertEquals(attachment, EvidenceDisposition.of("image/png", jpeg))
        assertEquals(attachment, EvidenceDisposition.of("image/jpeg", png))
        assertEquals(attachment, EvidenceDisposition.of("image/webp", "RIFF$\u0000\u0000\u0000WAVEfmt ".toByteArray(Charsets.ISO_8859_1)))
    }

    @Test
    fun `HTML spoofed as an allow-listed type is an attachment`() {
        assertEquals(attachment, EvidenceDisposition.of("application/pdf", html))
        assertEquals(attachment, EvidenceDisposition.of("image/png", html))
        assertEquals(attachment, EvidenceDisposition.of("application/json", html))
    }

    @Test
    fun `HTML is always an attachment`() {
        assertEquals(attachment, EvidenceDisposition.of("text/html", html))
        assertEquals(attachment, EvidenceDisposition.of("application/xhtml+xml", html))
    }

    @Test
    fun `SVG is always an attachment`() {
        assertEquals(attachment, EvidenceDisposition.of("image/svg+xml", svg))
    }

    @Test
    fun `Any other type is an attachment`() {
        assertEquals(attachment, EvidenceDisposition.of("application/octet-stream", pdf))
        assertEquals(attachment, EvidenceDisposition.of("application/zip", bytes(0x50, 0x4B, 0x03, 0x04)))
        assertEquals(attachment, EvidenceDisposition.of("text/xml", "<a/>".toByteArray()))
        assertEquals(attachment, EvidenceDisposition.of("application/javascript", "alert(1)".toByteArray()))
    }

    @Test
    fun `Declared type matched whatever its case and parameters`() {
        assertEquals(inline("application/pdf"), EvidenceDisposition.of("Application/PDF", pdf))
        assertEquals(
            inline("text/plain;charset=UTF-8"),
            EvidenceDisposition.of("text/plain; charset=ISO-8859-1", "Tests: 12 passed".toByteArray())
        )
    }

    @Test
    fun `A declared type which cannot be read is an attachment`() {
        assertEquals(attachment, EvidenceDisposition.of("not a type", pdf))
        assertEquals(attachment, EvidenceDisposition.of("", pdf))
    }

    @Test
    fun `JSON inline when it starts like a JSON object or array`() {
        assertEquals(inline("application/json"), EvidenceDisposition.of("application/json", """{"a":1}""".toByteArray()))
        assertEquals(inline("application/json"), EvidenceDisposition.of("application/json", "\n  [1, 2]".toByteArray()))
        assertEquals(
            inline("application/json"),
            EvidenceDisposition.of("application/json", bytes(0xEF, 0xBB, 0xBF) + """{"a":1}""".toByteArray())
        )
    }

    @Test
    fun `A JSON structured type is served as JSON`() {
        assertEquals(
            inline("application/json"),
            EvidenceDisposition.of("application/vnd.cyclonedx+json", """{"bomFormat":"CycloneDX"}""".toByteArray())
        )
    }

    @Test
    fun `JSON which does not start like JSON is an attachment`() {
        assertEquals(attachment, EvidenceDisposition.of("application/json", "\"just a string\"".toByteArray()))
        assertEquals(attachment, EvidenceDisposition.of("application/json", ByteArray(0)))
        assertEquals(attachment, EvidenceDisposition.of("application/json", bytes(0x7B, 0x00, 0x7D)))
    }

    @Test
    fun `Plain text inline as UTF-8 when it is UTF-8 text`() {
        assertEquals(
            inline("text/plain;charset=UTF-8"),
            EvidenceDisposition.of("text/plain", "Tests run: 12, Failures: 0 — ✓".toByteArray())
        )
    }

    @Test
    fun `Plain text cut in the middle of a character by the head is still text`() {
        // "é" is C3 A9: the head ends after its first byte
        assertEquals(
            inline("text/plain;charset=UTF-8"),
            EvidenceDisposition.of("text/plain", "Résumé".toByteArray().copyOf(2))
        )
    }

    @Test
    fun `Empty plain text is text`() {
        assertEquals(inline("text/plain;charset=UTF-8"), EvidenceDisposition.of("text/plain", ByteArray(0)))
    }

    @Test
    fun `Plain text which is not UTF-8 text is an attachment`() {
        assertEquals(attachment, EvidenceDisposition.of("text/plain", png))
        assertEquals(attachment, EvidenceDisposition.of("text/plain", bytes(0x41, 0x00, 0x42)))
        assertEquals(attachment, EvidenceDisposition.of("text/plain", bytes(0xC3, 0x28, 0x41)))
        assertEquals(attachment, EvidenceDisposition.of("text/plain", bytes(0xFF, 0xFE, 0x41, 0x00)))
    }
}
