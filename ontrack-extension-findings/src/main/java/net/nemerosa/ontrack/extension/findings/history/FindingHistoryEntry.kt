package net.nemerosa.ontrack.extension.findings.history

import net.nemerosa.ontrack.extension.findings.model.FindingAcceptance
import net.nemerosa.ontrack.extension.findings.model.FindingResolutionReason
import java.time.LocalDateTime

/**
 * Type of an entry in the history of a finding.
 */
enum class FindingHistoryEntryType {
    /**
     * Start of the first period of the first exposure of the finding
     */
    DISCOVERED,

    /**
     * Start of the first period of any other exposure
     */
    EXPOSED,

    /**
     * Start of any later period of an exposure
     */
    REOPENED,

    /**
     * End of a period
     */
    RESOLVED,

    /**
     * First observation of a period under an acceptance holding on the day of the observation
     */
    ACCEPTED,

    /**
     * Observation without acceptance after accepted ones
     */
    ACCEPTANCE_WITHDRAWN,

    /**
     * Day after the last day of an acceptance, evaluated when read
     */
    ACCEPTANCE_EXPIRED,

    /**
     * Consecutive observations of a period between two of its other entries
     */
    OBSERVATIONS,
}

/**
 * Entry in the history of a finding.
 *
 * @property type Type of the entry
 * @property time Time of the entry; for [observations][FindingHistoryEntryType.OBSERVATIONS], the
 * time of the last one
 * @property branchId ID of the branch
 * @property validationStampId ID of the validation stamp of the scans
 * @property validationRunId ID of the run of the entry, `null` once purged, or for an entry no
 * run made, like an expiry
 * @property build Display name of the build of the run, when it is known without the run: kept by
 * the period for its start and its end
 * @property resolutionReason For a [resolution][FindingHistoryEntryType.RESOLVED], its reason
 * @property acceptance For an acceptance entry, the acceptance
 * @property count For [observations][FindingHistoryEntryType.OBSERVATIONS], their number
 * @property firstTime For [observations][FindingHistoryEntryType.OBSERVATIONS], time of the first one
 * @property lastTime For [observations][FindingHistoryEntryType.OBSERVATIONS], time of the last one
 * @property firstValidationRunId For [observations][FindingHistoryEntryType.OBSERVATIONS], run of the first one
 * @property lastValidationRunId For [observations][FindingHistoryEntryType.OBSERVATIONS], run of the last one
 */
data class FindingHistoryEntry(
    val type: FindingHistoryEntryType,
    val time: LocalDateTime,
    val branchId: Int,
    val validationStampId: Int,
    val validationRunId: Int? = null,
    val build: String? = null,
    val resolutionReason: FindingResolutionReason? = null,
    val acceptance: FindingAcceptance? = null,
    val count: Int = 0,
    val firstTime: LocalDateTime? = null,
    val lastTime: LocalDateTime? = null,
    val firstValidationRunId: Int? = null,
    val lastValidationRunId: Int? = null,
)

/**
 * Stretch of a period during which the finding was reported under an acceptance holding.
 *
 * @property from Time of the first observation under the acceptance
 * @property to End of the stretch: the observation without it, the day after its last day, or the
 * end of the period. `null` while it holds.
 * @property acceptance Acceptance of the first observation
 * @property fromValidationRunId Run of the first observation
 */
data class FindingAcceptedSpan(
    val from: LocalDateTime,
    val to: LocalDateTime?,
    val acceptance: FindingAcceptance,
    val fromValidationRunId: Int,
)
