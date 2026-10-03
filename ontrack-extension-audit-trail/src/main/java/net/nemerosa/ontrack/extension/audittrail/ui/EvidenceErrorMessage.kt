package net.nemerosa.ontrack.extension.audittrail.ui

/**
 * Body of a refusal of an evidence: the message of the API errors, plus a stable code.
 *
 * @property status HTTP status
 * @property code Stable code of the error, like `audit-trail.evidence.too-large`
 * @property message Message, for a human
 */
data class EvidenceErrorMessage(
    val status: Int,
    val code: String,
    val message: String,
)
