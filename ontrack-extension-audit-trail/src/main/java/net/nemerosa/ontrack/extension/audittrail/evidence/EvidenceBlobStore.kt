package net.nemerosa.ontrack.extension.audittrail.evidence

import java.io.InputStream
import java.time.Instant

/**
 * The content of the evidences in the evidence storage, under `blobs/<sha256>` ([EvidenceBlobKeys]).
 *
 * Every operation needs the storage: it fails with [EvidenceError.STORAGE_NOT_CONFIGURED] when its
 * properties are not all set, and with [EvidenceError.STORAGE_UNREACHABLE] when its last probe
 * found it unreachable or when it fails to answer.
 */
interface EvidenceBlobStore {

    /**
     * Stages a content, streamed to the storage while it is hashed: the SHA-256 the server computes
     * is the key of its blob, whatever the client claimed.
     *
     * The content goes to an upload key, which [persist] copies to its blob. Nothing is left in
     * the storage when the content is refused; otherwise, the caller [discards][discard] the
     * staged content once done with it, persisted or not.
     *
     * @param content Content, read once
     * @param size Size of the content, as declared
     * @param expectedSha256 SHA-256 the client claimed, if any
     * @return The staged content
     * @throws EvidenceException [EvidenceError.TOO_LARGE] when the content is bigger than the
     * maximum size of an evidence, [EvidenceError.DIGEST_MISMATCH] when its SHA-256 is not the
     * claimed one, [EvidenceError.INVALID] when it is not of its declared size
     */
    fun stage(content: InputStream, size: Long, expectedSha256: String?): EvidenceStagedBlob

    /**
     * Copies a staged content to its blob, `blobs/<sha256>` — replacing the blob of the same
     * content, if any.
     *
     * Called under the [lock of the blob][EvidenceRepository.lockBlob], in the transaction which
     * writes the evidence referencing it: the collection of the blobs, which takes the same lock,
     * never removes a blob between its copy and the commit of its evidence.
     *
     * @param staged Staged content
     * @return The stored blob
     */
    fun persist(staged: EvidenceStagedBlob): EvidenceBlob

    /**
     * Deletes the upload key of a staged content — what is left behind, when this fails, is
     * removed by the sweep.
     *
     * @param staged Staged content
     */
    fun discard(staged: EvidenceStagedBlob)

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

    /**
     * Objects of the storage under one of the [prefixes][EvidenceBlobKeys] of the evidence, read
     * page by page while the sequence is consumed.
     *
     * @param prefix [EvidenceBlobKeys.BLOBS] or [EvidenceBlobKeys.UPLOADS]
     * @return Objects under this prefix, in the order of their keys
     */
    fun list(prefix: String): Sequence<EvidenceStoredObject>

    /**
     * Removes an object of the storage — nothing when it is absent.
     *
     * @param key Key of a blob or of an upload ([EvidenceBlobKeys]), nothing else
     * @throws IllegalArgumentException When the key is neither
     */
    fun remove(key: String)
}

/**
 * A content staged in the storage, under its upload key, before it is copied to its blob.
 *
 * @property uploadKey Key of the upload, `uploads/<uuid>`
 * @property sha256 SHA-256 of its content, computed by the server
 * @property size Size of its content, in bytes
 */
data class EvidenceStagedBlob(
    val uploadKey: String,
    val sha256: String,
    val size: Long,
)

/**
 * An object of the storage.
 *
 * @property key Its key
 * @property lastModified When it was last written — by the storage's clock
 */
data class EvidenceStoredObject(
    val key: String,
    val lastModified: Instant,
)

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
