package net.nemerosa.ontrack.extension.audittrail.storage

/**
 * State of the evidence storage.
 */
enum class EvidenceStorageState {

    /**
     * The storage properties are not all set: evidence cannot be attached.
     */
    NOT_CONFIGURED,

    /**
     * The storage is configured but its bucket cannot be reached — the service is down, the bucket
     * does not exist, or the credentials are refused: evidence cannot be attached.
     */
    UNREACHABLE,

    /**
     * The bucket is reachable.
     */
    OK,
}
