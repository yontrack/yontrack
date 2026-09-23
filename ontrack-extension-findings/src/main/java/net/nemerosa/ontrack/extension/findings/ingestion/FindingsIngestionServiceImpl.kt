package net.nemerosa.ontrack.extension.findings.ingestion

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.events.FindingsEvents
import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingObservation
import net.nemerosa.ontrack.extension.findings.model.FindingResolutionReason
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.extension.findings.report.FindingsReportFormatException
import net.nemerosa.ontrack.extension.findings.report.FindingsReportParser
import net.nemerosa.ontrack.extension.findings.report.FindingsReportUnsupportedFormatException
import net.nemerosa.ontrack.extension.findings.report.ParsedFindingsReport
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.extension.findings.state.FindingStateService
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataType
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class FindingsIngestionServiceImpl(
    parsers: List<FindingsReportParser>,
    private val structureService: StructureService,
    private val runInfoService: RunInfoService,
    private val findingRepository: FindingRepository,
    private val findingStateService: FindingStateService,
    private val eventPostService: EventPostService,
) : FindingsIngestionService {

    private val parsers: Map<String, FindingsReportParser> = parsers.associateBy { it.format }

    override val formats: Set<String> = this.parsers.keys

    override fun ingest(build: Build, request: FindingsIngestionRequest): FindingsIngestionResult {
        // Reading the report before creating anything
        val parser = parsers[request.format]
            ?: throw FindingsReportUnsupportedFormatException(request.format, formats)
        val report = parser.parse(request.report, request.scanner, request.kind)
        checkSizes(request.format, report)
        // Validation run with the counts of the findings
        val today = Time.now.toLocalDate()
        val findings = FindingsConsolidation.consolidate(report.findings, today)
        val run = structureService.newValidationRun(
            build = build,
            validationRunRequest = ValidationRunRequest(
                validationStampName = request.validation,
                description = request.description,
                dataTypeId = FindingsValidationDataType::class.java.name,
                data = FindingsConsolidation.counts(findings),
            )
        )
        if (request.runInfo != null) {
            runInfoService.setRunInfo(run, request.runInfo)
        }
        // Findings and their observations
        val written = writeFindings(build.project, report, findings, run)
        // Exposure of the findings on the branch, for the stamp
        val transitions = writeExposure(build.project, run, written)
        // Events of the transitions, in the same transaction, after the one of the run
        transitions.forEach { transition ->
            eventPostService.post(FindingsEvents.event(transition))
        }
        // OK
        return FindingsIngestionResult(run = run, transitions = transitions)
    }

    /**
     * Writes the findings and their observations.
     *
     * @return The findings of the scan, after their writing, with what the scan reported about
     * them
     */
    private fun writeFindings(
        project: Project,
        report: ParsedFindingsReport,
        findings: List<ConsolidatedFinding>,
        run: ValidationRun,
    ): List<Pair<Finding, ConsolidatedFinding>> {
        val time = run.runTime
        val existing = findingRepository.findFindingsByProjectAndScanner(project.id(), report.scanner)
            .associateBy { it.externalId to it.location }
        val written = findings.map { consolidated ->
            val finding = existing[consolidated.externalId to consolidated.location]
                ?.let { finding ->
                    finding.copy(
                        kind = report.kind,
                        title = consolidated.title,
                        url = consolidated.url,
                        lastSeen = maxOf(finding.lastSeen, time),
                        maxSeverity = minOf(finding.maxSeverity, consolidated.severity),
                    ).also {
                        findingRepository.updateFinding(it)
                    }
                }
                ?: findingRepository.insertFinding(
                    Finding(
                        id = 0,
                        projectId = project.id(),
                        scanner = report.scanner,
                        externalId = consolidated.externalId,
                        location = consolidated.location,
                        kind = report.kind,
                        title = consolidated.title,
                        url = consolidated.url,
                        firstSeen = time,
                        lastSeen = time,
                        resolvedAt = null,
                        maxSeverity = consolidated.severity,
                    )
                )
            finding to consolidated
        }
        findingRepository.insertObservations(
            written.map { (finding, consolidated) ->
                FindingObservation(
                    findingId = finding.id,
                    validationRunId = run.id(),
                    time = time,
                    severity = consolidated.severity,
                    rawSeverity = consolidated.rawSeverity,
                    installedVersion = consolidated.installedVersion,
                    fixedVersion = consolidated.fixedVersion,
                    acceptance = consolidated.acceptance,
                )
            }
        )
        return written
    }

    /**
     * Maintains the exposure of the findings on the branch of the run, for its stamp, and the
     * resolution time of the findings whose state in the project changed.
     *
     * @return Transitions of the findings on the branch
     */
    private fun writeExposure(
        project: Project,
        run: ValidationRun,
        written: List<Pair<Finding, ConsolidatedFinding>>,
    ): List<FindingTransition> {
        val time = run.runTime
        val branch = run.validationStamp.branch
        val validationStamp = run.validationStamp
        // Exposure rows
        val change = FindingsExposureComputation.compute(
            branchId = branch.id(),
            validationStampId = validationStamp.id(),
            branchExposures = findingRepository.findExposuresByBranch(branch.id()),
            reported = written.map { (finding, consolidated) ->
                ReportedExposure(
                    findingId = finding.id,
                    accepted = consolidated.accepted,
                    acceptanceExpiresAt = consolidated.acceptance?.expiresAt,
                )
            },
            time = time,
        )
        findingRepository.saveExposures(change.saved)
        // Resolution time of the findings, following their state in the project
        val reported = written.associate { (finding, _) -> finding.id to finding }
        val resolvedIds = change.saved.map { it.findingId }.filter { it !in reported }
        val touched = reported.values + findingRepository.findFindingsByIds(resolvedIds)
        val states = findingStateService.getFindingStates(project, touched, time.toLocalDate())
        val findings = touched.associate { finding ->
            val resolvedAt = when (states.getValue(finding.id)) {
                FindingState.RESOLVED -> finding.resolvedAt ?: time
                else -> null
            }
            finding.id to if (resolvedAt != finding.resolvedAt) {
                finding.copy(resolvedAt = resolvedAt).also { findingRepository.updateFinding(it) }
            } else {
                finding
            }
        }
        // Transitions
        val severities = written.associate { (finding, consolidated) -> finding.id to consolidated.severity }
        val lastSeverities = findingRepository.findLatestSeverities(
            validationStamp.id(),
            change.transitions
                .filter { it.type == FindingExposureTransitionType.RESOLVED }
                .map { it.findingId }
        )
        return change.transitions.map { transition ->
            val finding = findings.getValue(transition.findingId)
            FindingTransition(
                type = transition.type,
                finding = finding,
                branch = branch,
                validationStamp = validationStamp,
                severity = severities[finding.id]
                    ?: lastSeverities[finding.id]
                    ?: finding.maxSeverity,
                reopened = transition.reopened,
                resolutionReason = if (transition.type == FindingExposureTransitionType.RESOLVED) {
                    FindingResolutionReason.ABSENT
                } else {
                    null
                },
            )
        }
    }

    /**
     * Rejecting values which would not fit in the database, with the field they are in, rather
     * than failing on the insertion.
     */
    private fun checkSizes(format: String, report: ParsedFindingsReport) {
        val errors = mutableListOf<String>()
        fun check(path: String, value: String?, max: Int) {
            if (value != null && value.length > max) {
                errors += "`$path`: must not be longer than $max characters"
            }
        }
        check("scanner", report.scanner, MAX_SCANNER)
        report.findings.forEachIndexed { index, entry ->
            val path = "findings[$index]"
            check("$path.externalId", entry.externalId, MAX_EXTERNAL_ID)
            check("$path.location", entry.location, MAX_LOCATION)
            check("$path.rawSeverity", entry.rawSeverity, MAX_RAW_SEVERITY)
            check("$path.installedVersion", entry.installedVersion, MAX_VERSION)
            check("$path.fixedVersion", entry.fixedVersion, MAX_VERSION)
            check("$path.acceptance.source", entry.acceptance?.source, MAX_ACCEPTANCE_SOURCE)
        }
        if (errors.isNotEmpty()) {
            throw FindingsReportFormatException(format, errors)
        }
    }

    companion object {
        // Sizes of the columns, see the V83 migration
        private const val MAX_SCANNER = 100
        private const val MAX_EXTERNAL_ID = 255
        private const val MAX_LOCATION = 1024
        private const val MAX_RAW_SEVERITY = 100
        private const val MAX_VERSION = 255
        private const val MAX_ACCEPTANCE_SOURCE = 500
    }
}
