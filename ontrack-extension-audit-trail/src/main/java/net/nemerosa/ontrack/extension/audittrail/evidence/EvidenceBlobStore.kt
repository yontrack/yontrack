package net.nemerosa.ontrack.extension.audittrail.evidence

import java.io.InputStream

/**
 * The content of the evidences in the evidence storage, under `blobs/<sha256>` ([EvidenceBlobKeys]).
 *
 * Every operation needs the storage: it fails with [EvidenceError.STORAGE_NOT_CONFIGURED] when its
 * properties are not all set, and with [EvidenceError.STORAGE_UNREACHABLE] when its last probe
 * found it unreachable or when it fails to answer.
 */
interface EvidenceBlobStore {

    /**
     * Stores a content, streamed to the storage while it is hashed: the SHA-256 the server computes
     * is the key of the blob, whatever the client claimed.
     *
     * The content goes to an upload key first, then to its blob once hashed; the upload key is
     * deleted whatever happens. Nothing is stored when the content is refused.
     *
     * @param content Content, read once
     * @param size Size of the content, as declared
     * @param expectedSha256 SHA-256 the client claimed, if any
     * @return The stored blob
     * @throws EvidenceException [EvidenceError.TOO_LARGE] when the content is bigger than the
     * maximum size of an evidence, [EvidenceError.DIGEST_MISMATCH] when its SHA-256 is not the
     * claimed one, [EvidenceError.INVALID] when it is not of its declared size
     */
    fun store(content: InputStream, size: Long, expectedSha256: String?): EvidenceBlob

    /**
     * Opens the content of a blob.
     *
     * @param sha256 SHA-256 of the blob
     * @return The content, to close once read — `null` when the blob is absent
     */
    fun open(sha256: String): EvidenceBlobContent?

    /**
     * Checks a blob against its SHA-256, by reading it all.
     *
     * @param sha256 SHA-256 of the blob
     * @return Whether it is there and has this SHA-256
     */
    fun check(sha256: String): EvidenceBlobCheck
}

/**
 * A blob as stored.
 *
 * @property sha256 SHA-256 of its content, computed by the server
 * @property size Size of its content, in bytes
 */
data class EvidenceBlob(
    val sha256: String,
    val size: Long,
)

/**
 * The content of a blob, being read.
 *
 * @property stream Content, to close once read
 * @property size Size of the content, in bytes
 */
class EvidenceBlobContent(
    val stream: InputStream,
    val size: Long,
)

/**
 * Result of the check of a blob.
 */
enum class EvidenceBlobCheck {
    /**
     * The blob is there, with the expected SHA-256
     */
    OK,

    /**
     * The blob is absent from the storage
     */
    MISSING,

    /**
     * The blob is there, but its content no longer has the expected SHA-256
     */
    ALTERED,
}
