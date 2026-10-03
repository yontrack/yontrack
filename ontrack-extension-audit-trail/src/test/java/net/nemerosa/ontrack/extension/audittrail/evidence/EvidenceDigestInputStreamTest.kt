package net.nemerosa.ontrack.extension.audittrail.evidence

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The content of an evidence hashed and counted as it streams to the storage, and cut when it
 * grows past the maximum size.
 */
class EvidenceDigestInputStreamTest {

    // SHA-256 of "test", from any reference implementation
    private val testSha = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"

    @Test
    fun `Content hashed and counted as it is read`() {
        val stream = EvidenceDigestInputStream(ByteArrayInputStream("test".toByteArray()), maxSize = 10)
        assertEquals("test", stream.readAllBytes().decodeToString())
        assertEquals(testSha, stream.sha256)
        assertEquals(4, stream.size)
    }

    @Test
    fun `Content read byte by byte is hashed the same`() {
        val stream = EvidenceDigestInputStream(ByteArrayInputStream("test".toByteArray()), maxSize = 10)
        while (stream.read() >= 0) {
            // Reading
        }
        assertEquals(testSha, stream.sha256)
        assertEquals(4, stream.size)
    }

    @Test
    fun `Content of the maximum size gets through`() {
        val stream = EvidenceDigestInputStream(ByteArrayInputStream("test".toByteArray()), maxSize = 4)
        stream.readAllBytes()
        assertEquals(testSha, stream.sha256)
    }

    @Test
    fun `Content past the maximum size is cut`() {
        val stream = EvidenceDigestInputStream(ByteArrayInputStream("tests".toByteArray()), maxSize = 4)
        val ex = assertThrows<EvidenceException> { stream.readAllBytes() }
        assertEquals(EvidenceError.TOO_LARGE, ex.error)
    }

    @Test
    fun `Content past the maximum size is cut byte by byte`() {
        val stream = EvidenceDigestInputStream(ByteArrayInputStream("tests".toByteArray()), maxSize = 4)
        repeat(4) { stream.read() }
        val ex = assertThrows<EvidenceException> { stream.read() }
        assertEquals(EvidenceError.TOO_LARGE, ex.error)
    }

    @Test
    fun `The stream cannot be replayed`() {
        val stream = EvidenceDigestInputStream(ByteArrayInputStream("test".toByteArray()), maxSize = 10)
        assertFalse(stream.markSupported())
    }
}
