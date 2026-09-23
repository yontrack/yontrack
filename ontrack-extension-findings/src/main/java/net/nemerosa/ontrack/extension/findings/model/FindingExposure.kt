package net.nemerosa.ontrack.extension.findings.model

import java.time.LocalDateTime

/**
 * A finding is exposed on a branch while the latest scan of the same stamp on that branch
 * reports it.
 *
 * A deleted branch takes its exposure with it.
 *
 * @property findingId ID of the exposed finding
 * @property branchId ID of the branch the finding is exposed on
 * @property validationStampId ID of the validation stamp of the scans
 * @property since Time the exposure started
 */
data class FindingExposure(
    val findingId: Int,
    val branchId: Int,
    val validationStampId: Int,
    val since: LocalDateTime,
)
