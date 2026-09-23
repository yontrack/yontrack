package net.nemerosa.ontrack.extension.findings.query

import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingAcceptance
import net.nemerosa.ontrack.extension.findings.model.FindingExposureState
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.extension.findings.security.ProjectFindingsView
import net.nemerosa.ontrack.extension.findings.state.FindingStateService
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.jvm.optionals.getOrNull

@Service
@Transactional(readOnly = true)
class FindingQueryServiceImpl(
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val findingRepository: FindingRepository,
    private val findingStateService: FindingStateService,
) : FindingQueryService {

    override fun getProjectFindings(
        project: Project,
        filter: FindingFilter,
        offset: Int,
        size: Int,
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

        if (!filter.branch.isNullOrBlank()) {
            val branch = structureService.findBranchByName(project.name, filter.branch).getOrNull()
                ?: return page(emptyList(), offset, size)
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

        return page(findings.sortedWith(findingOrder), offset, size)
    }

    override fun getFindingsByExternalId(externalId: String): List<Finding> {
        val findings = findingRepository.findFindingsByExternalId(externalId)
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
        val branches = mutableMapOf<Int, Branch>()
        val stamps = mutableMapOf<Int, ValidationStamp>()
        return findingRepository.findExposuresByFinding(finding.id)
            .map { exposure ->
                FindingExposureView(
                    exposure = exposure,
                    branch = branches.getOrPut(exposure.branchId) {
                        structureService.getBranch(ID.of(exposure.branchId))
                    },
                    validationStamp = stamps.getOrPut(exposure.validationStampId) {
                        structureService.getValidationStamp(ID.of(exposure.validationStampId))
                    },
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
    ): PaginatedList<FindingObservationView> {
        if (!canSeeFindings(finding.projectId)) return PaginatedList.empty()
        return page(findingRepository.findObservationsByFinding(finding.id), offset, size)
            .map { observation ->
                FindingObservationView(
                    observation = observation,
                    finding = finding,
                    validationRun = structureService.getValidationRun(ID.of(observation.validationRunId)),
                )
            }
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
         * The most severe first, then the most recently seen.
         */
        private val findingOrder: Comparator<Finding> =
            compareBy<Finding> { it.maxSeverity.ordinal }
                .thenByDescending { it.lastSeen }
                .thenBy { it.externalId }
                .thenBy { it.location }
                .thenBy { it.scanner }
    }
}
