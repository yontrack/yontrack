package net.nemerosa.ontrack.extension.audittrail.evidence

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
}
