package net.nemerosa.ontrack.extension.findings.state

import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingExposureState
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.model.structure.BranchModelMatcherService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class FindingStateServiceImpl(
    private val structureService: StructureService,
    private val branchModelMatcherService: BranchModelMatcherService,
    private val findingRepository: FindingRepository,
) : FindingStateService {

    override fun getFindingState(finding: Finding, date: LocalDate): FindingState {
        val project = structureService.getProject(ID.of(finding.projectId))
        return getFindingStates(project, listOf(finding), date).getValue(finding.id)
    }

    override fun getFindingStates(
        project: Project,
        findings: Collection<Finding>,
        date: LocalDate,
    ): Map<Int, FindingState> {
        if (findings.isEmpty()) return emptyMap()
        val branches = countingBranches(project)
        val exposures = findingRepository.findExposuresByFindings(findings.map { it.id })
            .filter { it.branchId in branches }
            .groupBy { it.findingId }
        return findings.associate { finding ->
            val state = FindingExposureState.of(
                exposures[finding.id]?.map { it.stateOn(date) } ?: emptyList()
            )
            finding.id to when (state) {
                FindingExposureState.EXPOSED -> FindingState.OPEN
                FindingExposureState.ACCEPTED -> FindingState.ACCEPTED
                FindingExposureState.RESOLVED, null -> FindingState.RESOLVED
            }
        }
    }

    /**
     * IDs of the branches which count for the state of the findings of a project.
     */
    private fun countingBranches(project: Project): Set<Int> {
        val matcher = branchModelMatcherService.getBranchModelMatcher(project)
        return structureService.getBranchesForProject(project.id)
            .filter { !it.isDisabled && (matcher == null || matcher.matches(it)) }
            .map { it.id() }
            .toSet()
    }
}
