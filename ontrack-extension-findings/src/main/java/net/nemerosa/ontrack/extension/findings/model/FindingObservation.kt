package net.nemerosa.ontrack.extension.findings.model

import java.time.LocalDateTime

/**
 * One sighting of a finding by one scan of one build.
 *
 * An observation goes with its validation run: purging a build removes its observations,
 * never the finding.
 *
 * @property findingId ID of the observed finding
 * @property validationRunId ID of the validation run of the scan
 * @property time Time of the observation
 * @property severity Severity asserted by the scanner
 * @property rawSeverity Severity as the scanner gave it, for provenance
 * @property installedVersion Version of the component in which the finding was observed
 * @property fixedVersion Version of the component fixing the finding, if any
 * @property acceptance Acceptance of the finding at the time of the observation, if any
 */
data class FindingObservation(
    val findingId: Int,
    val validationRunId: Int,
    val time: LocalDateTime,
    val severity: FindingSeverity,
    val rawSeverity: String?,
    val installedVersion: String?,
    val fixedVersion: String?,
    val acceptance: FindingAcceptance?,
)
