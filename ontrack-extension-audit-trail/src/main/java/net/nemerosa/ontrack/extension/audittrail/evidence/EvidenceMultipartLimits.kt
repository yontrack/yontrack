package net.nemerosa.ontrack.extension.audittrail.evidence

import org.springframework.util.unit.DataSize

/**
 * Multipart limits of the instance — `spring.servlet.multipart.max-file-size` and
 * `max-request-size`, a negative size meaning no limit.
 *
 * The servlet container applies them to every multipart request, before any controller: an
 * evidence bigger than they allow would never reach the upload. They are raised to let the biggest
 * evidence through, the evidence upload enforcing its own maximum size as it streams the content.
 *
 * @property maxFileSize Maximum size of a file
 * @property maxRequestSize Maximum size of a whole request
 */
data class EvidenceMultipartLimits(
    val maxFileSize: DataSize,
    val maxRequestSize: DataSize,
) {

    /**
     * Limits raised for an evidence of [evidenceMaxSize], never lowered: the file size to it, the
     * request size to it plus [FIELDS_ALLOWANCE] for the other fields of the form.
     */
    fun raisedFor(evidenceMaxSize: DataSize): EvidenceMultipartLimits =
        EvidenceMultipartLimits(
            maxFileSize = atLeast(maxFileSize, evidenceMaxSize),
            maxRequestSize = atLeast(maxRequestSize, DataSize.ofBytes(evidenceMaxSize.toBytes() + FIELDS_ALLOWANCE.toBytes())),
        )

    private fun atLeast(limit: DataSize, size: DataSize): DataSize =
        if (limit.isNegative || limit >= size) limit else size

    companion object {
        /**
         * Room left in a request for the fields sent with the evidence
         */
        val FIELDS_ALLOWANCE: DataSize = DataSize.ofMegabytes(1)
    }
}
