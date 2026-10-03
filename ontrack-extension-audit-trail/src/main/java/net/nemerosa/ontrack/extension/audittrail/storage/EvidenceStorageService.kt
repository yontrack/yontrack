package net.nemerosa.ontrack.extension.audittrail.storage

/**
 * The storage of the evidence: an S3-compatible bucket, configured by the
 * `ontrack.extension.audit-trail.storage.*` properties.
 *
 * Its status is probed every minute by [EvidenceStorageProbeJob] and cached in between, so that
 * reading it — on every page, through the global messages — never calls the storage.
 */
interface EvidenceStorageService {

    /**
     * Status of the storage, as the last probe found it — the storage is probed at once when it
     * never was.
     */
    val status: EvidenceStorageStatus

    /**
     * Probes the storage now, and caches its status.
     *
     * @return New status of the storage
     */
    fun checkStatus(): EvidenceStorageStatus

    /**
     * Client of the storage, `null` when the storage is not configured.
     */
    val client: EvidenceStorageClient?
}
