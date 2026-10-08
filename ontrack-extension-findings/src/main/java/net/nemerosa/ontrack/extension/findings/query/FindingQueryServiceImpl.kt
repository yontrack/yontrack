package net.nemerosa.ontrack.extension.findings.query

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.history.FindingHistoryComputation
import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingExposurePeriod
import net.nemerosa.ontrack.extension.findings.model.FindingAcceptance
import net.nemerosa.ontrack.extension.findings.model.FindingExposureState
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.extension.findings.model.RankedFinding
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.extension.findings.security.ProjectFindingsView
import net.nemerosa.ontrack.extension.findings.state.FindingStateService
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.BuildDisplayNameService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDate
import kotlin.jvm.optionals.getOrNull

@Service
@Transactional(readOnly = true)
class FindingQueryServiceImpl(
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val findingRepository: FindingRepository,
    private val findingStateService: FindingStateService,
    private val buildDisplayNameService: BuildDisplayNameService,
) : FindingQueryService {

    override fun getProjectFindings(
        project: Project,
        filter: FindingFilter,
        offset: Int,
        size: Int,
        sort: FindingSort,
        date: LocalDate,
    ): PaginatedList<Finding> {
        if (!canSeeFindings(project.id())) return PaginatedList.empty()

        var findings = if (filter.scanner.isNullOrBlank()) {
            findingRepository.findFindingsByProject(project.id())
        } else {
            findingRepository.findFindingsByProjectAndScanner(project.id(), filter.scanner)
        }
        findings = findings.filter { finding ->
            (filter.severity == null || finding.maxSeverity == filter.severity) &&
                    (filter.kind == null || finding.kind == filter.kind)
        }

        var filterBranch: Branch? = null
        if (!filter.branch.isNullOrBlank()) {
            val branch = structureService.findBranchByName(project.name, filter.branch).getOrNull()
                ?: return page(emptyList(), offset, size)
            filterBranch = branch
            // On a given branch, the state is the one on this branch, whether it counts for the
            // project or not
            val exposures = findingRepository.findExposuresByBranch(branch.id()).groupBy { it.findingId }
            findings = findings.filter { finding ->
                val branchExposures = exposures[finding.id]
                branchExposures != null && (
                        filter.state == null ||
                                FindingState.of(FindingExposureState.of(branchExposures.map { it.stateOn(date) })) == filter.state
                        )
            }
        } else if (filter.state != null) {
            val states = findingStateService.getFindingStates(project, findings, date)
            findings = findings.filter { states[it.id] == filter.state }
        }

        val exposedFor = when (sort) {
            FindingSort.DEFAULT -> emptyMap()
            FindingSort.EXPOSED_FOR -> loadExposedFor(
                findingIds = findings.map { it.id },
                branchIds = periodBranchIds(project, filterBranch),
                date = date,
            )
        }
        return page(findings.sortedWith(sort.comparator(exposedFor)), offset, size)
    }

    override fun getFindingsExposedFor(
        findings: Collection<Finding>,
        branch: String?,
        date: LocalDate,
    ): Map<Int, FindingExposedForView> {
        val now = Time.now
        val branches = mutableMapOf<Int, Branch>()
        val stamps = mutableMapOf<Int, ValidationStamp>()
        return findings.groupBy { it.projectId }.flatMap { (projectId, projectFindings) ->
            val project = visibleProject(projectId) ?: return@flatMap emptyList()
            val branchIds = if (branch.isNullOrBlank()) {
                periodBranchIds(project, null)
            } else {
                // A branch the project does not have: no period counts
                structureService.findBranchByName(project.name, branch).getOrNull()
                    ?.let { periodBranchIds(project, it) }
                    ?: emptySet()
            }
            loadExposedFor(projectFindings.map { it.id }, branchIds, date).map { (findingId, exposedFor) ->
                val ongoing = exposedFor.ongoing
                findingId to FindingExposedForView(
                    exposedFor = exposedFor,
                    branch = ongoing?.let {
                        branches.getOrPut(it.branchId) { structureService.getBranch(ID.of(it.branchId)) }
                    },
                    validationStamp = ongoing?.let {
                        stamps.getOrPut(it.validationStampId) {
                            structureService.getValidationStamp(ID.of(it.validationStampId))
                        }
                    },
                    ongoingSeconds = ongoing?.let { Duration.between(it.startedAt, now).seconds.coerceAtLeast(0) },
                    lastEpisodeSeconds = exposedFor.lastEpisode?.duration(now)?.seconds,
                )
            }
        }.toMap()
    }

    /**
     * IDs of the branches whose periods say how long a finding has been exposed: the given branch
     * only, or, with none, the branches which count toward the state of the findings in the project.
     */
    private fun periodBranchIds(project: Project, branch: Branch?): Set<Int> =
        branch?.let { setOf(it.id()) } ?: findingStateService.getCountingBranchIds(project)

    /**
     * How long some findings of a project have been exposed on some of its branches, their
     * periods and their exposures loaded in one query each.
     *
     * @param branchIds IDs of the branches whose periods count
     */
    private fun loadExposedFor(
        findingIds: Collection<Int>,
        branchIds: Set<Int>,
        date: LocalDate,
    ): Map<Int, FindingExposedFor> {
        if (findingIds.isEmpty()) return emptyMap()
        val periods = findingRepository.findExposurePeriodsByFindings(findingIds)
            .filter { it.branchId in branchIds }
            .groupBy { it.findingId }
        val exposures = findingRepository.findExposuresByFindings(periods.keys)
            .groupBy { it.findingId }
        return findingIds.associateWith { findingId ->
            FindingExposedFor.of(periods[findingId] ?: emptyList(), exposures[findingId] ?: emptyList(), date)
        }
    }

    override fun getProjectFindingsSummary(project: Project, date: LocalDate): FindingsSummary? {
        if (!canSeeFindings(project.id())) return null
        val findings = findingRepository.findFindingsByProject(project.id())
        val states = findingStateService.getFindingStates(project, findings, date)
        val byId = findings.associateBy { it.id }
        // Exposure per branch: open on a branch when exposed there for one of its stamps at least,
        // as the filter on a branch gives it
        val exposures = if (findings.isEmpty()) {
            emptyList()
        } else {
            findingRepository.findExposuresByFindings(findings.map { it.id })
        }
        val countingBranches = findingStateService.getCountingBranchIds(project)
        val branches = exposures.groupBy { it.branchId }
            .map { (branchId, branchExposures) ->
                val open = branchExposures.groupBy { it.findingId }
                    .filterValues { findingExposures ->
                        FindingExposureState.of(findingExposures.map { it.stateOn(date) }) == FindingExposureState.EXPOSED
                    }
                    .keys
                    .mapNotNull { byId[it] }
                FindingsBranchSummary(
                    branch = structureService.getBranch(ID.of(branchId)),
                    open = severityCounts(open),
                    counting = branchId in countingBranches,
                )
            }
            .sortedWith(branchSummaryOrder)
        return FindingsSummary(
            open = severityCounts(findings.filter { states[it.id] == FindingState.OPEN }),
            acceptedCount = findings.count { states[it.id] == FindingState.ACCEPTED },
            resolvedCount = findings.count { states[it.id] == FindingState.RESOLVED },
            branches = branches,
            scanners = findings.map { it.scanner }.distinct().sorted(),
        )
    }

    override fun getBranchFindingsSummary(branch: Branch, date: LocalDate): BranchFindingsSummary? {
        if (!canSeeFindings(branch.project.id())) return null
        // State on the branch: its exposure rolled up over its stamps, as the filter on a branch gives it
        val states = findingRepository.findExposuresByBranch(branch.id())
            .groupBy { it.findingId }
            .mapValues { (_, exposures) -> FindingExposureState.of(exposures.map { it.stateOn(date) }) }
        val exposed = states.filterValues { it == FindingExposureState.EXPOSED }.keys
        val open = if (exposed.isEmpty()) emptyList() else findingRepository.findFindingsByIds(exposed)
        return BranchFindingsSummary(
            open = severityCounts(open),
            acceptedCount = states.values.count { it == FindingExposureState.ACCEPTED },
            resolvedCount = states.values.count { it == FindingExposureState.RESOLVED },
        )
    }

    private fun severityCounts(findings: Collection<Finding>): Map<FindingSeverity, Int> {
        val counts = findings.groupingBy { it.maxSeverity }.eachCount()
        return FindingSeverity.entries.associateWith { counts[it] ?: 0 }
    }

    override fun getFindingsByExternalId(externalId: String, projectIds: Collection<Int>?): List<Finding> {
        val findings = findingRepository.findFindingsByExternalId(externalId, projectIds)
        val projects = findings.map { it.projectId }.distinct()
            .mapNotNull { projectId -> visibleProject(projectId) }
            .associateBy { it.id() }
        return findings
            .filter { it.projectId in projects }
            .sortedWith(
                compareBy<Finding> { projects.getValue(it.projectId).name }
                    .thenBy { it.scanner }
                    .thenBy { it.location }
            )
    }

    override fun getRankedFindings(projectIds: Collection<Int>, size: Int, date: LocalDate): List<RankedFinding> {
        val countingBranchIds = countingBranchIds(projectIds)
        if (countingBranchIds.isEmpty()) return emptyList()
        return findingRepository.findRankedFindings(
            countingBranchIds = countingBranchIds,
            date = date,
            size = size.coerceIn(0, FindingQueryService.MAX_RANKED_FINDINGS),
        )
    }

    override fun getSearchedFindings(
        projectIds: Collection<Int>,
        text: String,
        size: Int,
        date: LocalDate,
    ): List<RankedFinding> {
        val searched = text.trim()
        if (searched.isEmpty()) return emptyList()
        val countingBranchIds = countingBranchIds(projectIds)
        if (countingBranchIds.isEmpty()) return emptyList()
        return findingRepository.findSearchedFindings(
            countingBranchIds = countingBranchIds,
            text = searched,
            date = date,
            size = size.coerceIn(0, FindingQueryService.MAX_RANKED_FINDINGS),
        )
    }

    /**
     * The branches which count toward the state of the findings, for each of the projects whose
     * findings the user can see, by project ID.
     */
    private fun countingBranchIds(projectIds: Collection<Int>): Map<Int, Set<Int>> =
        projectIds.distinct()
            .mapNotNull { projectId -> visibleProject(projectId) }
            .associate { project -> project.id() to findingStateService.getCountingBranchIds(project) }

    override fun findFindingById(id: Int): Finding? =
        findingRepository.findFindingById(id)?.takeIf { canSeeFindings(it.projectId) }

    override fun getValidationRunFindings(run: ValidationRun): List<FindingObservationView> {
        if (!canSeeFindings(run.project.id())) return emptyList()
        val observations = findingRepository.findObservationsByValidationRun(run.id())
        if (observations.isEmpty()) return emptyList()
        val findings = findingRepository.findFindingsByIds(observations.map { it.findingId }.distinct())
            .associateBy { it.id }
        return observations
            .mapNotNull { observation ->
                findings[observation.findingId]?.let { finding ->
                    FindingObservationView(
                        observation = observation,
                        finding = finding,
                        validationRun = run,
                    )
                }
            }
            .sortedWith(
                compareBy<FindingObservationView> { it.observation.severity.ordinal }
                    .thenBy { it.finding.externalId }
                    .thenBy { it.finding.location }
            )
    }

    override fun getFindingState(finding: Finding, date: LocalDate): FindingState? =
        if (canSeeFindings(finding.projectId)) {
            findingStateService.getFindingState(finding, date)
        } else {
            null
        }

    override fun getFindingExposures(finding: Finding): List<FindingExposureView> {
        if (!canSeeFindings(finding.projectId)) return emptyList()
        val exposures = findingRepository.findExposuresByFinding(finding.id)
        if (exposures.isEmpty()) return emptyList()
        val countingBranches = findingStateService.getCountingBranchIds(
            structureService.getProject(ID.of(finding.projectId))
        )
        val branches = mutableMapOf<Int, Branch>()
        val stamps = mutableMapOf<Int, ValidationStamp>()
        return exposures
            .map { exposure ->
                FindingExposureView(
                    exposure = exposure,
                    branch = branches.getOrPut(exposure.branchId) {
                        structureService.getBranch(ID.of(exposure.branchId))
                    },
                    validationStamp = stamps.getOrPut(exposure.validationStampId) {
                        structureService.getValidationStamp(ID.of(exposure.validationStampId))
                    },
                    counts = exposure.branchId in countingBranches,
                )
            }
            .sortedWith(
                compareBy<FindingExposureView> { it.branch.name }
                    .thenBy { it.validationStamp.name }
            )
    }

    override fun getFindingObservations(
        finding: Finding,
        offset: Int,
        size: Int,
        filter: FindingObservationFilter,
    ): PaginatedList<FindingObservationView> {
        if (!canSeeFindings(finding.projectId)) return PaginatedList.empty()
        val observations = if (filter == FindingObservationFilter()) {
            findingRepository.findObservationsByFinding(finding.id)
        } else {
            findingRepository.findObservationSightingsByFinding(finding.id)
                .filter { sighting ->
                    val time = sighting.observation.time
                    (filter.branchId == null || sighting.branchId == filter.branchId) &&
                            (filter.validationStampId == null || sighting.validationStampId == filter.validationStampId) &&
                            (filter.from == null || time >= filter.from) &&
                            (filter.to == null || time <= filter.to)
                }
                .map { it.observation }
        }
        return page(observations, offset, size)
            .map { observation ->
                FindingObservationView(
                    observation = observation,
                    finding = finding,
                    validationRun = structureService.getValidationRun(ID.of(observation.validationRunId)),
                )
            }
    }

    override fun getExposurePeriods(exposure: FindingExposureView, date: LocalDate): List<FindingExposurePeriodView> {
        val findingId = exposure.exposure.findingId
        val finding = findingRepository.findFindingById(findingId) ?: return emptyList()
        if (!canSeeFindings(finding.projectId)) return emptyList()
        val periods = findingRepository.findExposurePeriodsByFinding(findingId)
            .filter { it.branchId == exposure.branch.id() && it.validationStampId == exposure.validationStamp.id() }
        if (periods.isEmpty()) return emptyList()
        val sightings = findingRepository.findObservationSightingsByFinding(findingId)
        val now = Time.now
        val runs = RunCache()
        return periods.map { period ->
            val startedBy = runs[period.startedByValidationRunId]
            val endedBy = runs[period.endedByValidationRunId]
            FindingExposurePeriodView(
                period = period,
                startedBy = startedBy,
                startedInBuild = buildName(startedBy, period.startedInBuild),
                endedBy = endedBy,
                endedInBuild = buildName(endedBy, period.endedInBuild),
                durationSeconds = Duration.between(period.startedAt, period.endedAt ?: now).seconds.coerceAtLeast(0),
                acceptedSpans = FindingHistoryComputation.acceptedSpans(period, sightings, date).map { span ->
                    val run = runs[span.fromValidationRunId]
                    FindingAcceptedSpanView(
                        span = span,
                        fromValidationRun = run,
                        fromBuild = buildName(run, null),
                    )
                },
            )
        }
    }

    override fun getFindingFirstSeenIn(finding: Finding): FindingSighting? {
        if (!canSeeFindings(finding.projectId)) return null
        val period = findingRepository.findExposurePeriodsByFinding(finding.id)
            .minWithOrNull(FindingHistoryComputation.periodOrder)
            ?: return null
        val run = RunCache()[period.startedByValidationRunId]
        return FindingSighting(
            time = period.startedAt,
            branch = structureService.getBranch(ID.of(period.branchId)),
            validationStamp = structureService.getValidationStamp(ID.of(period.validationStampId)),
            validationRun = run,
            build = buildName(run, period.startedInBuild),
        )
    }

    override fun getFindingResolvedIn(finding: Finding, date: LocalDate): FindingSighting? {
        if (!canSeeFindings(finding.projectId)) return null
        if (findingStateService.getFindingState(finding, date) != FindingState.RESOLVED) return null
        val countingBranches = findingStateService.getCountingBranchIds(
            structureService.getProject(ID.of(finding.projectId))
        )
        val period = findingRepository.findExposurePeriodsByFinding(finding.id)
            .filter { it.branchId in countingBranches && it.endedAt != null }
            .maxWithOrNull(compareBy<FindingExposurePeriod> { it.endedAt }.thenBy { it.id })
            ?: return null
        val run = RunCache()[period.endedByValidationRunId]
        return FindingSighting(
            time = period.endedAt!!,
            branch = structureService.getBranch(ID.of(period.branchId)),
            validationStamp = structureService.getValidationStamp(ID.of(period.validationStampId)),
            validationRun = run,
            build = buildName(run, period.endedInBuild),
        )
    }

    override fun getFindingHistory(
        finding: Finding,
        offset: Int,
        size: Int,
        date: LocalDate,
    ): PaginatedList<FindingHistoryEntryView> {
        if (!canSeeFindings(finding.projectId)) return PaginatedList.empty()
        val history = FindingHistoryComputation.history(
            periods = findingRepository.findExposurePeriodsByFinding(finding.id),
            sightings = findingRepository.findObservationSightingsByFinding(finding.id),
            today = date,
        )
        val branches = mutableMapOf<Int, Branch>()
        val stamps = mutableMapOf<Int, ValidationStamp>()
        val runs = RunCache()
        return page(history, offset, size).map { entry ->
            val run = runs[entry.validationRunId]
            val firstRun = runs[entry.firstValidationRunId]
            val lastRun = runs[entry.lastValidationRunId]
            FindingHistoryEntryView(
                entry = entry,
                branch = branches.getOrPut(entry.branchId) { structureService.getBranch(ID.of(entry.branchId)) },
                validationStamp = stamps.getOrPut(entry.validationStampId) {
                    structureService.getValidationStamp(ID.of(entry.validationStampId))
                },
                validationRun = run,
                build = buildName(run, entry.build),
                firstValidationRun = firstRun,
                firstBuild = buildName(firstRun, null),
                lastValidationRun = lastRun,
                lastBuild = buildName(lastRun, null),
            )
        }
    }

    /**
     * Display name of the build of a run while it exists, else the one which was kept
     */
    private fun buildName(run: ValidationRun?, kept: String?): String? =
        run?.build?.let { build -> buildDisplayNameService.getFirstBuildDisplayName(build) ?: build.name } ?: kept

    /**
     * Runs by ID, each loaded once. A run referenced by a period or an observation exists: a
     * purged one is no longer referenced.
     */
    private inner class RunCache {
        private val runs = mutableMapOf<Int, ValidationRun>()

        operator fun get(id: Int?): ValidationRun? =
            id?.let { runs.getOrPut(it) { structureService.getValidationRun(ID.of(it)) } }
    }

    override fun getFindingAcceptance(finding: Finding): FindingAcceptance? =
        if (canSeeFindings(finding.projectId)) {
            findingRepository.findObservationsByFinding(finding.id).firstOrNull()?.acceptance
        } else {
            null
        }

    /**
     * The findings of a project can be seen by a user who can see the project and who is granted
     * [ProjectFindingsView] on it. The latter alone does not grant the project.
     */
    private fun canSeeFindings(projectId: Int): Boolean = visibleProject(projectId) != null

    private fun visibleProject(projectId: Int): Project? =
        structureService.findProjectByID(ID.of(projectId))?.takeIf {
            securityService.isProjectFunctionGranted(projectId, ProjectFindingsView::class.java)
        }

    private fun <T> page(items: List<T>, offset: Int, size: Int): PaginatedList<T> =
        if (offset >= items.size) {
            PaginatedList.create(emptyList(), offset, size, items.size)
        } else {
            PaginatedList.create(items, offset, size)
        }

    companion object {

        /**
         * The most exposed branches first: by number of critical findings, then of high ones,
         * and so on, then by name.
         */
        private val branchSummaryOrder: Comparator<FindingsBranchSummary> =
            Comparator<FindingsBranchSummary> { a, b ->
                FindingSeverity.entries
                    .map { severity -> (b.open[severity] ?: 0).compareTo(a.open[severity] ?: 0) }
                    .firstOrNull { it != 0 }
                    ?: 0
            }.thenBy { it.branch.name }
    }
}
