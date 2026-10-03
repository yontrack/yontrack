package net.nemerosa.ontrack.extension.audittrail.evidence

import java.io.InputStream

/**
 * An evidence as a client sends it, before it is checked and normalized ([EvidenceInput]).
 *
 * @property fileName Name of the file
 * @property mediaType Declared media type
 * @property size Size of the content, in bytes
 * @property content Opens the content — called once, after the upload was authorized
 * @property sourceTool Tool which produced the evidence
 * @property sourceVersion Version of the tool
 * @property sourceUrl Where the evidence was produced
 * @property externalDigest SHA-256 the client claims for the content
 */
class EvidenceUpload(
    val fileName: String?,
    val mediaType: String?,
    val size: Long,
    val content: () -> InputStream,
    val sourceTool: String? = null,
    val sourceVersion: String? = null,
    val sourceUrl: String? = null,
    val externalDigest: String? = null,
)
