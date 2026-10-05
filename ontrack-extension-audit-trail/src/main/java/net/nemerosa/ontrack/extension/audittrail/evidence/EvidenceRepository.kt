package net.nemerosa.ontrack.extension.audittrail.evidence

import java.time.LocalDateTime

/**
 * Storage of the metadata of the evidences — their content being in the evidence storage.
 */
interface EvidenceRepository {

    /**
     * Inserts an evidence.
     *
     * @param evidence Evidence, whose ID is ignored
     * @param canonicalCollectedBy Canonical JSON of its actor
     * @return Evidence with its ID
     */
    fun insert(evidence: Evidence, canonicalCollectedBy: String): Evidence

    /**
     * Evidence by ID, `null` when there is none.
     */
    fun findById(id: Int): Evidence?

    /**
     * Evidences attached to a validation run, deleted ones included, in the order of their upload.
     */
    fun findByValidationRun(validationRunId: Int): List<Evidence>

    /**
     * Evidences attached to the validation runs of a build, deleted ones included, in the order of
     * their upload.
     */
    fun findByBuild(buildId: Int): List<Evidence>

    /**
     * Marks an evidence as deleted, its row being kept.
     *
     * @param id ID of the evidence
     * @param deletedAt Time of the deletion
     * @return Whether the evidence was marked — `false` when there is no such evidence, or when it
     * was deleted already
     */
    fun markDeleted(id: Int, deletedAt: LocalDateTime): Boolean

    /**
     * Locks the blob of a content until the end of the current transaction: the attachment of an
     * evidence and the collection of the blobs, which both take this lock, are serialized for
     * one blob.
     *
     * @param sha256 SHA-256 of the content
     */
    fun lockBlob(sha256: String)

    /**
     * Whether an evidence which is not deleted references the blob of a content.
     */
    fun isBlobReferenced(sha256: String): Boolean

    /**
     * Among the blobs of some contents, those an evidence which is not deleted references.
     *
     * @param sha256s SHA-256 of the contents
     * @return The SHA-256 of those referenced
     */
    fun findReferencedBlobs(sha256s: Collection<String>): Set<String>

    /**
     * Number and total size of the evidences which are not deleted, per project — the projects
     * without any being left out.
     */
    fun getProjectStats(): List<EvidenceProjectStats>
}

/**
 * Number and total size of the evidences of a project which are not deleted.
 *
 * @property project Name of the project
 * @property count Number of evidences
 * @property size Total size of their contents, in bytes — a content shared by several evidences
 * counted for each
 */
data class EvidenceProjectStats(
    val project: String,
    val count: Long,
    val size: Long,
)
