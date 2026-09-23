package net.nemerosa.ontrack.extension.findings.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A finding is exposed on a branch while the latest scan of the same stamp on that branch
 * reports it.
 *
 * The comparison scope is `(stamp, branch)`: the scan of one stamp never resolves what another
 * stamp reports. A row outlives the resolution of its exposure, so that a return after
 * resolution can be told from a first exposure.
 *
 * A deleted branch takes its exposure with it.
 *
 * @property findingId ID of the exposed finding
 * @property branchId ID of the branch the finding is exposed on
 * @property validationStampId ID of the validation stamp of the scans
 * @property since Time the current exposure started
 * @property accepted Whether the latest scan reported the finding under an acceptance holding
 * on the day of the scan
 * @property acceptanceExpiresAt Last day this acceptance holds, if any
 * @property resolvedAt Time the finding was resolved on this branch for this stamp, if it is
 * @property resolutionReason Why the finding was resolved, if it is
 */
data class FindingExposure(
    val findingId: Int,
    val branchId: Int,
    val validationStampId: Int,
    val since: LocalDateTime,
    val accepted: Boolean = false,
    val acceptanceExpiresAt: LocalDate? = null,
    val resolvedAt: LocalDateTime? = null,
    val resolutionReason: FindingResolutionReason? = null,
) {

    /**
     * State as recorded by the latest scan, the expiry of the acceptance not evaluated.
     */
    val recordedState: FindingExposureState
        get() = when {
            resolvedAt != null -> FindingExposureState.RESOLVED
            accepted -> FindingExposureState.ACCEPTED
            else -> FindingExposureState.EXPOSED
        }

    /**
     * State on a given day: an acceptance past its expiry stops counting when it is read.
     */
    fun stateOn(date: LocalDate): FindingExposureState = when {
        resolvedAt != null -> FindingExposureState.RESOLVED
        accepted && (acceptanceExpiresAt == null || !acceptanceExpiresAt.isBefore(date)) ->
            FindingExposureState.ACCEPTED

        else -> FindingExposureState.EXPOSED
    }
}
