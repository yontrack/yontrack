package net.nemerosa.ontrack.extension.audittrail.evidence

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertEquals

/**
 * Keys of the evidence in the storage: content-addressed by their SHA-256, never named after
 * anything a client sends.
 */
class EvidenceBlobKeysTest {

    private val sha = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"

    @Test
    fun `The key of a blob is its SHA-256 under blobs`() {
        assertEquals("blobs/$sha", EvidenceBlobKeys.blob(sha))
    }

    @Test
    fun `The key of a blob is only ever a lowercase SHA-256`() {
        listOf(
            "../$sha".take(64),
            sha.uppercase(),
            sha.dropLast(1),
            "$sha/..",
            "",
            "${sha.dropLast(1)}/",
        ).forEach { value ->
            assertThrows<IllegalArgumentException>(value) { EvidenceBlobKeys.blob(value) }
        }
    }

    @Test
    fun `The key of an upload in progress is a UUID under uploads`() {
        val uuid = UUID.fromString("0b1e7f4c-5d0a-4a43-9a3c-2f0d6b8e1c11")
        assertEquals("uploads/0b1e7f4c-5d0a-4a43-9a3c-2f0d6b8e1c11", EvidenceBlobKeys.upload(uuid))
    }
}
