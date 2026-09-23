package net.nemerosa.ontrack.extension.findings.ingestion

import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.model.FindingResolutionReason
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.RunInfoInput
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationStamp
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

/**
 * Posting the report of a security scan as a validation run of a build.
 */
interface FindingsIngestionService {

    /**
     * Reads a report and, in one transaction, creates the validation run with the counts of the
     * findings, writes the findings and their observations by this run, and maintains their
     * exposure on the branch of the build for the stamp of the run. Each transition of a finding
     * on the branch is posted as an event — see
     * [FindingsEvents][net.nemerosa.ontrack.extension.findings.events.FindingsEvents] — in the same
     * transaction, in the order of the transitions.
     *
     * A report which cannot be read creates nothing, and neither does a report in a native format
     * posted without the licensed feature for it — see
     * [FindingsLicense][net.nemerosa.ontrack.extension.findings.license.FindingsLicense].
     *
     * @param build Build to validate
     * @param request What to post
     * @return The created validation run and the transitions of the findings on the branch
     */
    fun ingest(build: Build, request: FindingsIngestionRequest): FindingsIngestionResult

    /**
     * Formats which can be ingested
     */
    val formats: Set<String>
}

/**
 * Report of a security scan to post as a validation run.
 *
 * @property validation Name of the validation stamp
 * @property description Description of the validation run
 * @property runInfo Run info of the validation run
 * @property format Format of the report
 * @property kind Kind of scan, taking precedence over the report's
 * @property scanner Name of the scanner, taking precedence over the report's
 * @property report Report
 * @property dateTime Time of the scan, the moment of the ingestion when `null`. It dates the run,
 * and the observations, the exposure and the resolution of the findings follow the time of the run —
 * which is what lets a replayed or seeded history read as the history it stands for.
 */
data class FindingsIngestionRequest(
    val validation: String,
    val description: String? = null,
    val runInfo: RunInfoInput? = null,
    val format: String,
    val kind: FindingKind? = null,
    val scanner: String? = null,
    val report: JsonNode,
    val dateTime: LocalDateTime? = null,
)

/**
 * Outcome of the ingestion of a report.
 *
 * @property run Created validation run
 * @property transitions Transitions of the findings on the branch of the run, caused by this
 * scan: new exposures, returns after resolution or acceptance, resolutions. In the order of the
 * report, then the resolutions.
 */
data class FindingsIngestionResult(
    val run: ValidationRun,
    val transitions: List<FindingTransition>,
)

/**
 * Transition of a finding on a branch, caused by a scan.
 *
 * @property type Type of transition
 * @property finding Finding, as it is after the scan
 * @property branch Branch of the scan
 * @property validationStamp Stamp of the scan
 * @property severity Severity of the finding: as observed by this scan for a new exposure, as
 * last observed by the scans of this stamp for a resolution — the maximum severity of the finding
 * when those observations were purged
 * @property reopened For a [new exposure][FindingExposureTransitionType.NEW], whether the finding
 * was known on the branch before: resolved there, or accepted there
 * @property resolutionReason For a [resolution][FindingExposureTransitionType.RESOLVED], why
 */
data class FindingTransition(
    val type: FindingExposureTransitionType,
    val finding: Finding,
    val branch: Branch,
    val validationStamp: ValidationStamp,
    val severity: FindingSeverity,
    val reopened: Boolean,
    val resolutionReason: FindingResolutionReason?,
)
