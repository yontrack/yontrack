package net.nemerosa.ontrack.extension.audittrail.evidence

import java.time.Instant

/**
 * Collection of the objects of the evidence storage which nothing references any longer: the blobs
 * of the evidences which are all deleted — or gone with their validation runs — and the uploads
 * left behind by an interrupted upload.
 *
 * A blob is removed only when no evidence which is not deleted references it, under the
 * [lock of the blob][EvidenceRepository.lockBlob] which the attachment of an evidence takes while
 * it copies the blob and writes the evidence: a blob is never removed between its copy and the
 * commit of the evidence which references it.
 */
interface EvidenceBlobCollector {

    /**
     * Removes the blob of a content when no evidence which is not deleted references it.
     *
     * @param sha256 SHA-256 of the content
     * @return Whether the blob was removed — `false` when it is still referenced
     * @throws EvidenceException When the storage cannot be used
     */
    fun collect(sha256: String): Boolean

    /**
     * Sweeps the storage: removes the blobs which no evidence which is not deleted references,
     * and the uploads, as long as they were written before [writtenBefore] — the delay avoids
     * racing the uploads in progress.
     *
     * @param writtenBefore Only the objects written before this time are removed
     * @return What was removed
     * @throws EvidenceException When the storage cannot be used
     */
    fun sweep(writtenBefore: Instant): EvidenceSweep
}

/**
 * Result of a [sweep][EvidenceBlobCollector.sweep] of the evidence storage.
 *
 * @property removedBlobs Number of blobs removed
 * @property removedUploads Number of uploads removed
 */
data class EvidenceSweep(
    val removedBlobs: Int,
    val removedUploads: Int,
)
