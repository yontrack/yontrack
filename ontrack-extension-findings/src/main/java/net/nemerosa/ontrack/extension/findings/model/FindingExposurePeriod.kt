package net.nemerosa.ontrack.extension.findings.model

import java.time.LocalDateTime

/**
 * One continuous stretch of an [exposure][FindingExposure]: from the first run which reported the
 * finding to the first run which no longer did. An exposure has one or more periods; a reopening
 * starts a new one.
 *
 * The runs go with their builds when these are purged; the names of the builds are kept, so that
 * the build a finding was fixed in stays readable.
 *
 * @property id ID of the period
 * @property findingId ID of the exposed finding
 * @property branchId ID of the branch the finding is exposed on
 * @property validationStampId ID of the validation stamp of the scans
 * @property startedAt Time of the run which started the period
 * @property startedByValidationRunId ID of this run, `null` once purged, or for a period older
 * than the periods themselves
 * @property startedInBuild Display name of the build of this run, when the period started
 * @property endedAt Time of the run which ended the period, `null` while it is open
 * @property endedByValidationRunId ID of this run, `null` once purged, or while the period is open
 * @property endedInBuild Display name of the build of this run, when the period ended
 * @property resolutionReason Why the period ended, if it did
 */
data class FindingExposurePeriod(
    val id: Int,
    val findingId: Int,
    val branchId: Int,
    val validationStampId: Int,
    val startedAt: LocalDateTime,
    val startedByValidationRunId: Int?,
    val startedInBuild: String?,
    val endedAt: LocalDateTime? = null,
    val endedByValidationRunId: Int? = null,
    val endedInBuild: String? = null,
    val resolutionReason: FindingResolutionReason? = null,
) {
    /**
     * Whether the period is still open
     */
    val ongoing: Boolean get() = endedAt == null
}
