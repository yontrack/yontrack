package net.nemerosa.ontrack.extension.audittrail.evidence

/**
 * Why an evidence is refused: a stable code for the clients — the CLI, the KDSL, the UI — and the
 * HTTP status it is answered with.
 *
 * @property code Stable code of the error, answered with it
 * @property status HTTP status of the error
 */
enum class EvidenceError(
    val code: String,
    val status: Int,
) {

    /**
     * The storage properties are not all set.
     */
    STORAGE_NOT_CONFIGURED("audit-trail.evidence.storage-not-configured", 503),

    /**
     * The storage is configured but cannot be reached.
     */
    STORAGE_UNREACHABLE("audit-trail.evidence.storage-unreachable", 503),

    /**
     * The evidence is bigger than the instance allows.
     */
    TOO_LARGE("audit-trail.evidence.too-large", 413),

    /**
     * The digest the client claimed is not the digest of what it sent.
     */
    DIGEST_MISMATCH("audit-trail.evidence.digest-mismatch", 422),

    /**
     * The licence does not allow the audit trail: no evidence is attached.
     */
    NOT_LICENSED("audit-trail.evidence.not-licensed", 403),

    /**
     * What was sent with the evidence cannot be accepted: a media type, a digest, a source...
     */
    INVALID("audit-trail.evidence.invalid", 400),
}
