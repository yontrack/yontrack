package net.nemerosa.ontrack.extension.audittrail.evidence

import java.io.InputStream

/**
 * An evidence being downloaded.
 *
 * @property evidence Evidence
 * @property disposition How to serve it, decided from its declared type and its first bytes
 * @property stream Its whole content, first bytes included — to close once sent
 * @property size Size of the content, in bytes, as stored
 */
class EvidenceDownload(
    val evidence: Evidence,
    val disposition: EvidenceDisposition,
    val stream: InputStream,
    val size: Long,
)
