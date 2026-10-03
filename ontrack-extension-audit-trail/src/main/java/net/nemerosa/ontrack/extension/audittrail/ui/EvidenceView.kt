package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.extension.audittrail.evidence.Evidence
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceSource
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

/**
 * An evidence, as the API returns it.
 */
@APIName("Evidence")
@APIDescription("A file attached to a validation run, referenced by an evidence.attached entry of the trail of its build")
data class EvidenceView(
    @APIDescription("ID of the evidence")
    val id: Int,
    @APIDescription("ID of the validation run the evidence is attached to")
    val validationRunId: Int,
    @APIDescription("Name of the file - a label, never a path")
    val fileName: String,
    @APIDescription("Media type declared by the client - the download serves it as such only for the allow-listed types whose content agrees with it")
    val mediaType: String,
    @APIDescription("Size of the content, in bytes")
    val size: Long,
    @APIDescription("SHA-256 of the content as the server computed it, in lowercase hexadecimal")
    val sha256: String,
    @APIDescription("Server time of the upload, UTC")
    val collectedAt: LocalDateTime,
    @APIDescription("Actor of the upload, as entries carry it")
    val collectedBy: JsonNode,
    @APIDescription("Where the client says the evidence comes from")
    val source: EvidenceSource?,
    @APIDescription("SHA-256 the client claimed, which matched the computed one")
    val externalDigest: String?,
    @APIDescription("When the evidence was deleted, null while it is not")
    val deletedAt: LocalDateTime?,
    @APIDescription("Path of the download of the content, relative to the root of the API - null once the evidence is deleted")
    val downloadUrl: String?,
) {
    companion object {
        /**
         * Path of the download of an evidence, relative to the root of the API.
         */
        fun downloadUrl(id: Int) = "/rest/extension/audit-trail/evidence/$id/download"

        fun of(evidence: Evidence) = EvidenceView(
            id = evidence.id,
            validationRunId = evidence.validationRunId,
            fileName = evidence.fileName,
            mediaType = evidence.mediaType,
            size = evidence.size,
            sha256 = evidence.sha256,
            collectedAt = evidence.collectedAt,
            collectedBy = evidence.collectedBy,
            source = evidence.source,
            externalDigest = evidence.externalDigest,
            deletedAt = evidence.deletedAt,
            downloadUrl = if (evidence.deletedAt == null) downloadUrl(evidence.id) else null,
        )
    }
}
