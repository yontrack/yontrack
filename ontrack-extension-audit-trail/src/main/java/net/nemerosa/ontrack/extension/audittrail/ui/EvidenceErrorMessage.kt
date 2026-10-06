package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceException
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

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
) {
    companion object {

        /**
         * Answer to a refusal of an evidence: its status, and its message with its code.
         *
         * @param ex Refusal
         */
        fun response(ex: EvidenceException): ResponseEntity<EvidenceErrorMessage> =
            ResponseEntity.status(ex.error.status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    EvidenceErrorMessage(
                        status = ex.error.status,
                        code = ex.error.code,
                        message = ex.message ?: ex.error.code,
                    )
                )
    }
}
