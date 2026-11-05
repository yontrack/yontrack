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

    override fun getFindingState(finding: Finding, date: LocalDate): FindingState? {
        val project = structureService.getProject(ID.of(finding.projectId))
        return getFindingStates(project, listOf(finding), date)[finding.id]
    }

    override fun getFindingStates(
        project: Project,
        findings: Collection<Finding>,
        date: LocalDate,
    ): Map<Int, FindingState> {
        if (findings.isEmpty()) return emptyMap()
        val counting = getCountingBranchIds(project)
        val disabled = getDisabledBranchIds(project)
        val exposures = findingRepository.findExposuresByFindings(findings.map { it.id })
            .groupBy { it.findingId }
        return findings.mapNotNull { finding ->
            val findingExposures = exposures[finding.id] ?: emptyList()
            if (findingExposures.isNotEmpty() && findingExposures.all { it.branchId in disabled }) {
                null
            } else {
                val state = FindingExposureState.of(
                    findingExposures.filter { it.branchId in counting }.map { it.stateOn(date) }
                )
                finding.id to FindingState.of(state)
            }
        }.toMap()
    }

    override fun getCountingBranchIds(project: Project): Set<Int> {
        val matcher = branchModelMatcherService.getBranchModelMatcher(project)
        return structureService.getBranchesForProject(project.id)
            .filter { !it.isDisabled && (matcher == null || matcher.matches(it)) }
            .map { it.id() }
            .toSet()
    }

    override fun getDisabledBranchIds(project: Project): Set<Int> =
        structureService.getBranchesForProject(project.id)
            .filter { it.isDisabled }
            .map { it.id() }
            .toSet()
}
