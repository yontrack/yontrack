package net.nemerosa.ontrack.extension.audittrail.evidence

import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.*

/**
 * The content of an evidence, hashed with SHA-256 and counted as it is read — on its way to the
 * storage — and cut with [EvidenceError.TOO_LARGE] as soon as it grows past [maxSize].
 *
 * It cannot be marked nor reset: every byte is hashed once, in order, and a client of the stream
 * which would replay it fails rather than sending other bytes than those hashed.
 *
 * @param input Content
 * @param maxSize Maximum number of bytes of the content
 */
class EvidenceDigestInputStream(
    input: InputStream,
    private val maxSize: Long,
) : FilterInputStream(input) {

    private val digest = MessageDigest.getInstance("SHA-256")

    /**
     * Number of bytes read so far
     */
    var size: Long = 0
        private set

    /**
     * SHA-256 of the bytes read so far, in lowercase hexadecimal — the one of the content once
     * the stream is read to its end.
     */
    val sha256: String
        get() = HexFormat.of().formatHex((digest.clone() as MessageDigest).digest())

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) {
            count(1)
            digest.update(b.toByte())
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) {
            count(n)
            digest.update(b, off, n)
        }
        return n
    }

    override fun skip(n: Long): Long = throw UnsupportedOperationException("The content of an evidence cannot be skipped.")

    override fun markSupported(): Boolean = false

    override fun mark(readlimit: Int) {
        // Not supported
    }

    override fun reset() = throw UnsupportedOperationException("The content of an evidence cannot be replayed.")

    private fun count(n: Int) {
        size += n
        if (size > maxSize) {
            throw EvidenceException(
                EvidenceError.TOO_LARGE,
                "The evidence is bigger than the maximum size of $maxSize bytes."
            )
        }
    }
}
