package net.nemerosa.ontrack.extension.audittrail.evidence

import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

/**
 * A file attached to a validation run, referenced by an `evidence.attached` entry of the trail of
 * its build. Immutable: a new upload is a new evidence.
 *
 * @property id ID of the evidence
 * @property validationRunId ID of the validation run it is attached to
 * @property fileName Name of the file, a label only ([EvidenceInput.fileName])
 * @property mediaType Media type declared by the client ([EvidenceInput.mediaType]) — never served
 * as such outside the allow-list of [EvidenceDisposition]
 * @property size Size of the content, in bytes
 * @property sha256 SHA-256 of the content as the server computed it, in lowercase hexadecimal —
 * the key of its blob ([EvidenceBlobKeys.blob])
 * @property collectedAt Server time of the upload, UTC
 * @property collectedBy Actor of the upload, as entries carry it
 * @property source Where the client says the evidence comes from
 * @property externalDigest SHA-256 the client claimed, which matched [sha256]
 * @property deletedAt When the evidence was deleted, `null` while it is not
 */
data class Evidence(
    val id: Int,
    val validationRunId: Int,
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val sha256: String,
    val collectedAt: LocalDateTime,
    val collectedBy: JsonNode,
    val source: EvidenceSource?,
    val externalDigest: String?,
    val deletedAt: LocalDateTime?,
)
