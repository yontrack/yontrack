package net.nemerosa.ontrack.extension.findings.ingestion

import net.nemerosa.ontrack.extension.findings.model.FindingExposure
import net.nemerosa.ontrack.extension.findings.model.FindingExposureState
import net.nemerosa.ontrack.extension.findings.model.FindingResolutionReason
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A finding as reported by a scan, for its exposure.
 *
 * @property findingId ID of the finding
 * @property accepted Whether the finding is reported under an acceptance holding on the day of
 * the scan
 * @property acceptanceExpiresAt Last day this acceptance holds, if any
 */
data class ReportedExposure(
    val findingId: Int,
    val accepted: Boolean,
    val acceptanceExpiresAt: LocalDate?,
)

/**
 * What a scan changes to the exposure of a branch.
 *
 * @property saved Exposure rows to save, created or changed
 * @property transitions Transitions of the findings on the branch, in the order of the report,
 * then the resolutions
 */
data class ExposureChange(
    val saved: List<FindingExposure>,
    val transitions: List<ExposureTransition>,
)

/**
 * Transition of a finding on a branch.
 *
 * @property findingId ID of the finding
 * @property type Type of transition
 * @property reopened For a [new exposure][FindingExposureTransitionType.NEW], whether the finding
 * was known on the branch before — resolved, or accepted
 */
data class ExposureTransition(
    val findingId: Int,
    val type: FindingExposureTransitionType,
    val reopened: Boolean = false,
)

/**
 * Maintaining the exposure of the findings on a branch from the latest scan of one stamp.
 */
object FindingsExposureComputation {

    /**
     * The latest scan of a stamp on a branch is the reference for this stamp: a finding it
     * reports is exposed, a finding it no longer reports is resolved, with reason
     * [ABSENT][FindingResolutionReason.ABSENT]. The rows of the other stamps are never changed.
     *
     * The transitions are those of the branch, all its stamps rolled up, so that a finding still
     * reported by another stamp of the branch is not resolved on the branch:
     *
     * - [NEW][FindingExposureTransitionType.NEW] when the finding becomes exposed without
     *   acceptance where it was not. A finding first seen accepted is not new; a finding reported
     *   without acceptance after being accepted — its acceptance withdrawn or past its expiry — is,
     *   and so is a return after resolution: both are flagged [reopened][ExposureTransition.reopened].
     * - [RESOLVED][FindingExposureTransitionType.RESOLVED] when the finding is no longer reported
     *   on the branch while it was exposed there on the day of the scan. A finding which was only
     *   accepted is resolved silently: no new exposure was ever signalled for it.
     *
     * @param branchId ID of the branch of the scan
     * @param validationStampId ID of the stamp of the scan
     * @param branchExposures Current exposure rows of the branch, for all its stamps
     * @param reported Findings reported by the scan
     * @param time Time of the scan
     * @return Rows to save and transitions
     */
    fun compute(
        branchId: Int,
        validationStampId: Int,
        branchExposures: List<FindingExposure>,
        reported: List<ReportedExposure>,
        time: LocalDateTime,
    ): ExposureChange {
        val date = time.toLocalDate()
        val current = branchExposures
            .filter { it.validationStampId == validationStampId }
            .associateBy { it.findingId }
        val reportedIds = reported.map { it.findingId }.toSet()

        // Rows of this stamp
        val saved = mutableListOf<FindingExposure>()
        reported.forEach { finding ->
            val row = current[finding.findingId]
            val exposure = if (row == null || row.resolvedAt != null) {
                FindingExposure(
                    findingId = finding.findingId,
                    branchId = branchId,
                    validationStampId = validationStampId,
                    since = time,
                    accepted = finding.accepted,
                    acceptanceExpiresAt = finding.acceptanceExpiresAt,
                )
            } else {
                row.copy(
                    accepted = finding.accepted,
                    acceptanceExpiresAt = finding.acceptanceExpiresAt,
                )
            }
            if (exposure != row) {
                saved += exposure
            }
        }
        current.values
            .filter { it.resolvedAt == null && it.findingId !in reportedIds }
            .forEach { row ->
                saved += row.copy(
                    resolvedAt = time,
                    resolutionReason = FindingResolutionReason.ABSENT,
                )
            }

        // Transitions on the branch
        val before = branchExposures.groupBy { it.findingId }
        val transitions = saved.mapNotNull { exposure ->
            val beforeRows = before[exposure.findingId] ?: emptyList()
            val afterRows = beforeRows.filter { it.validationStampId != validationStampId } + exposure
            val beforeRecorded = FindingExposureState.of(beforeRows.map { it.recordedState })
            val afterRecorded = FindingExposureState.of(afterRows.map { it.recordedState })
            when {
                afterRecorded == FindingExposureState.EXPOSED && beforeRecorded != FindingExposureState.EXPOSED ->
                    ExposureTransition(
                        findingId = exposure.findingId,
                        type = FindingExposureTransitionType.NEW,
                        reopened = beforeRecorded != null,
                    )

                afterRecorded == FindingExposureState.RESOLVED &&
                        FindingExposureState.of(beforeRows.map { it.stateOn(date) }) == FindingExposureState.EXPOSED ->
                    ExposureTransition(
                        findingId = exposure.findingId,
                        type = FindingExposureTransitionType.RESOLVED,
                    )

                else -> null
            }
        }

        return ExposureChange(saved = saved, transitions = transitions)
    }
}
