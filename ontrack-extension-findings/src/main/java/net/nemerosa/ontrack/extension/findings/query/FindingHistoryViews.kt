package net.nemerosa.ontrack.extension.findings.query

import net.nemerosa.ontrack.extension.findings.history.FindingAcceptedSpan
import net.nemerosa.ontrack.extension.findings.history.FindingHistoryEntry
import net.nemerosa.ontrack.extension.findings.model.FindingExposurePeriod
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationStamp
import java.time.LocalDateTime

/**
 * Period of an exposure, with its runs and the names of their builds.
 *
 * @property startedBy Run which started the period, `null` once purged
 * @property startedInBuild Display name of the build of this run: the one of the build while it
 * exists, else the one kept by the period
 * @property endedBy Run which ended the period, `null` once purged or while it is open
 * @property endedInBuild Display name of the build of this run, as for [startedInBuild]
 * @property durationSeconds Duration of the period, until now while it is open
 * @property acceptedSpans Stretches of the period under an acceptance, the oldest first
 */
data class FindingExposurePeriodView(
    val period: FindingExposurePeriod,
    val startedBy: ValidationRun?,
    val startedInBuild: String?,
    val endedBy: ValidationRun?,
    val endedInBuild: String?,
    val durationSeconds: Long,
    val acceptedSpans: List<FindingAcceptedSpanView>,
)

/**
 * Stretch of a period under an acceptance, with the run and the build of its first observation.
 */
data class FindingAcceptedSpanView(
    val span: FindingAcceptedSpan,
    val fromValidationRun: ValidationRun?,
    val fromBuild: String?,
)

/**
 * Where and when a finding was seen at a given moment: its discovery, or its resolution.
 *
 * @property validationRun Run, `null` once purged, or for a period older than the periods
 * @property build Display name of the build of the run, `null` when unknown
 */
data class FindingSighting(
    val time: LocalDateTime,
    val branch: Branch,
    val validationStamp: ValidationStamp,
    val validationRun: ValidationRun?,
    val build: String?,
)

/**
 * Entry in the history of a finding, with its branch, its stamp, its runs and their builds.
 *
 * @property validationRun Run of the entry, `null` once purged, or when no run made it
 * @property build Display name of the build of this run, or the one kept by its period
 * @property firstValidationRun For a group of observations, the run of the first one
 * @property firstBuild For a group of observations, the display name of its build
 * @property lastValidationRun For a group of observations, the run of the last one
 * @property lastBuild For a group of observations, the display name of its build
 */
data class FindingHistoryEntryView(
    val entry: FindingHistoryEntry,
    val branch: Branch,
    val validationStamp: ValidationStamp,
    val validationRun: ValidationRun?,
    val build: String?,
    val firstValidationRun: ValidationRun? = null,
    val firstBuild: String? = null,
    val lastValidationRun: ValidationRun? = null,
    val lastBuild: String? = null,
)

/**
 * Filter on the observations of a finding: those of a branch and a stamp, within a time range,
 * both ends included. Every field is optional.
 */
data class FindingObservationFilter(
    val branchId: Int? = null,
    val validationStampId: Int? = null,
    val from: LocalDateTime? = null,
    val to: LocalDateTime? = null,
)
