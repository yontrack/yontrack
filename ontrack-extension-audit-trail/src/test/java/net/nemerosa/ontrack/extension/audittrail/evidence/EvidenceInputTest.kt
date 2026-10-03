package net.nemerosa.ontrack.extension.audittrail.evidence

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a client sends with an evidence, made safe to store, to hash in the trail and to serve back.
 */
class EvidenceInputTest {

    // File names

    @Test
    fun `A plain file name is kept`() {
        assertEquals("trivy-report.pdf", EvidenceInput.fileName("trivy-report.pdf"))
        assertEquals("rapport été.pdf", EvidenceInput.fileName("rapport été.pdf"))
    }

    @Test
    fun `Directories are dropped from the file name`() {
        assertEquals("passwd", EvidenceInput.fileName("../../etc/passwd"))
        assertEquals("evil.exe", EvidenceInput.fileName("..\\..\\Windows\\evil.exe"))
        assertEquals("report.pdf", EvidenceInput.fileName("/tmp/build/report.pdf"))
    }

    @Test
    fun `A file name made of dots only is replaced`() {
        assertEquals(EvidenceInput.DEFAULT_FILE_NAME, EvidenceInput.fileName(".."))
        assertEquals(EvidenceInput.DEFAULT_FILE_NAME, EvidenceInput.fileName("a/.."))
        assertEquals(EvidenceInput.DEFAULT_FILE_NAME, EvidenceInput.fileName("."))
    }

    @Test
    fun `A missing or blank file name is replaced`() {
        assertEquals(EvidenceInput.DEFAULT_FILE_NAME, EvidenceInput.fileName(null))
        assertEquals(EvidenceInput.DEFAULT_FILE_NAME, EvidenceInput.fileName("   "))
        assertEquals(EvidenceInput.DEFAULT_FILE_NAME, EvidenceInput.fileName("dir/"))
    }

    @Test
    fun `Control characters are removed from the file name`() {
        assertEquals("report.pdfSet-Cookie: x=y", EvidenceInput.fileName("report.pdf\r\nSet-Cookie: x=y"))
        assertEquals("ab", EvidenceInput.fileName("a\u0000b"))
        assertEquals("ab", EvidenceInput.fileName("a\u007Fb"))
    }

    @Test
    fun `Bidirectional overrides are removed from the file name`() {
        // "invoice<RLO>fdp.exe" displays as "invoiceexe.pdf"
        assertEquals("invoicefdp.exe", EvidenceInput.fileName("invoice‮fdp.exe"))
        assertEquals("ab", EvidenceInput.fileName("a⁦b"))
    }

    @Test
    fun `A long file name is cut, keeping its extension`() {
        val name = EvidenceInput.fileName("a".repeat(300) + ".json")
        assertEquals(EvidenceInput.MAX_FILE_NAME_LENGTH, name.length)
        assertEquals("a".repeat(EvidenceInput.MAX_FILE_NAME_LENGTH - 5) + ".json", name)
    }

    // Media types

    @Test
    fun `A media type is kept as its lowercase essence`() {
        assertEquals("application/pdf", EvidenceInput.mediaType("application/pdf"))
        assertEquals("text/plain", EvidenceInput.mediaType("Text/Plain; charset=UTF-8"))
        assertEquals("application/vnd.cyclonedx+json", EvidenceInput.mediaType("application/vnd.cyclonedx+json"))
    }

    @Test
    fun `A missing media type is application octet-stream`() {
        assertEquals("application/octet-stream", EvidenceInput.mediaType(null))
        assertEquals("application/octet-stream", EvidenceInput.mediaType(" "))
    }

    @Test
    fun `A media type which cannot be read is refused`() {
        listOf("pdf", "text/", "*/*", "image/*", "text/html\r\nX-Injected: 1", "a/b/c").forEach { type ->
            val ex = assertThrows<EvidenceException> { EvidenceInput.mediaType(type) }
            assertEquals(EvidenceError.INVALID, ex.error)
        }
    }

    // External digests

    private val sha = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"

    @Test
    fun `No external digest`() {
        assertNull(EvidenceInput.externalDigest(null))
        assertNull(EvidenceInput.externalDigest(""))
    }

    @Test
    fun `An external digest is a SHA-256, prefixed or not, in lowercase`() {
        assertEquals(sha, EvidenceInput.externalDigest(sha))
        assertEquals(sha, EvidenceInput.externalDigest("sha256:$sha"))
        assertEquals(sha, EvidenceInput.externalDigest("SHA256:${sha.uppercase()}"))
    }

    @Test
    fun `An external digest which is not a SHA-256 is refused`() {
        listOf("md5:098f6bcd4621d373cade4e832627b4f6", "sha256:abc", "sha512:$sha$sha", "$sha ", "z".repeat(64)).forEach { digest ->
            val ex = assertThrows<EvidenceException> { EvidenceInput.externalDigest(digest) }
            assertEquals(EvidenceError.INVALID, ex.error)
        }
    }

    // Sources

    @Test
    fun `No source`() {
        assertNull(EvidenceInput.source(null, null, null))
        assertNull(EvidenceInput.source(" ", "", null))
    }

    @Test
    fun `A source`() {
        assertEquals(
            EvidenceSource(tool = "trivy", version = "0.58.1", url = "https://ci.example.com/job/1"),
            EvidenceInput.source(" trivy ", "0.58.1", "https://ci.example.com/job/1"),
        )
        assertEquals(EvidenceSource(tool = "zap", version = null, url = null), EvidenceInput.source("zap", null, null))
    }

    @Test
    fun `A source URL is an HTTP or HTTPS URL`() {
        listOf(
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "file:///etc/passwd",
            "ci.example.com/job/1",
            "https://",
            "https://ci.example.com/a b",
        ).forEach { url ->
            val ex = assertThrows<EvidenceException>(url) { EvidenceInput.source("tool", null, url) }
            assertEquals(EvidenceError.INVALID, ex.error)
        }
    }

    @Test
    fun `Source values are limited in length`() {
        val ex = assertThrows<EvidenceException> { EvidenceInput.source("t".repeat(256), null, null) }
        assertEquals(EvidenceError.INVALID, ex.error)
        assertThrows<EvidenceException> { EvidenceInput.source("t", "v".repeat(256), null) }
        assertThrows<EvidenceException> { EvidenceInput.source("t", null, "https://e.com/" + "u".repeat(2000)) }
    }

    @Test
    fun `Control characters are refused in a source`() {
        assertThrows<EvidenceException> { EvidenceInput.source("trivy\nX", null, null) }
    }
}
