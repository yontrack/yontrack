package net.nemerosa.ontrack.extension.findings.query

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingExposure
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.extension.findings.repository.FindingRepository
import net.nemerosa.ontrack.extension.findings.security.ProjectFindingsView
import net.nemerosa.ontrack.extension.findings.state.FindingStateService
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationStamp
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * A widespread external ID has many findings per project: a project's branches are worked out once
 * for all of them, not once per finding.
 */
class FindingQueryServiceBatchTest {

    private val time = LocalDateTime.of(2026, 10, 1, 12, 0)
    private val today = LocalDate.of(2026, 10, 20)

    private val structureService = mockk<StructureService>()
    private val securityService = mockk<SecurityService>()
    private val findingRepository = mockk<FindingRepository>()
    private val findingStateService = mockk<FindingStateService>()

    private val service = FindingQueryServiceImpl(
        structureService = structureService,
        securityService = securityService,
        findingRepository = findingRepository,
        findingStateService = findingStateService,
        buildDisplayNameService = mockk(),
    )

    private val visible = mockk<Project>()
    private val hidden = mockk<Project>()

    private val first = finding(id = 1, projectId = 10)
    private val second = finding(id = 2, projectId = 10)
    private val other = finding(id = 3, projectId = 20)

    @BeforeEach
    fun before() {
        every { structureService.findProjectByID(ID.of(10)) } returns visible
        every { structureService.findProjectByID(ID.of(20)) } returns hidden
        every { securityService.isProjectFunctionGranted(10, ProjectFindingsView::class.java) } returns true
        every { securityService.isProjectFunctionGranted(20, ProjectFindingsView::class.java) } returns false
        every { findingStateService.getCountingBranchIds(visible) } returns setOf(100)
        every { findingStateService.getDisabledBranchIds(visible) } returns setOf(102)
        every { structureService.getBranch(any()) } answers {
            mockk<Branch> { every { name } returns "branch-${firstArg<ID>().value}" }
        }
        every { structureService.getValidationStamp(any()) } answers {
            mockk<ValidationStamp> { every { name } returns "scan" }
        }
    }

    @Test
    fun `The exposures of the findings of a project list its branches once, without the hidden projects and the disabled branches`() {
        every { findingRepository.findExposuresByFindings(listOf(1, 2)) } returns listOf(
            exposure(findingId = 1, branchId = 101),
            exposure(findingId = 1, branchId = 100),
            exposure(findingId = 2, branchId = 100),
            exposure(findingId = 2, branchId = 102),
        )

        val exposures = service.getFindingsExposures(listOf(first, second, other))

        assertEquals(
            mapOf(
                1 to listOf("branch-100: true", "branch-101: false"),
                2 to listOf("branch-100: true"),
            ),
            exposures.mapValues { (_, views) -> views.map { "${it.branch.name}: ${it.counts}" } },
        )
        verify(exactly = 1) { findingStateService.getCountingBranchIds(visible) }
        verify(exactly = 1) { findingStateService.getDisabledBranchIds(visible) }
        verify(exactly = 1) { findingRepository.findExposuresByFindings(any()) }
    }

    @Test
    fun `The states of the findings of a project are read at once, without the hidden projects`() {
        every { findingStateService.getFindingStates(visible, listOf(first, second), today) } returns mapOf(
            1 to FindingState.OPEN,
            2 to FindingState.RESOLVED,
        )

        assertEquals(
            mapOf(1 to FindingState.OPEN, 2 to FindingState.RESOLVED),
            service.getFindingStates(listOf(first, second, other), today),
        )
        verify(exactly = 1) { findingStateService.getFindingStates(any(), any(), any()) }
    }

    private fun exposure(findingId: Int, branchId: Int) = FindingExposure(
        findingId = findingId,
        branchId = branchId,
        validationStampId = 1000,
        since = time,
    )

    private fun finding(id: Int, projectId: Int) = Finding(
        id = id,
        projectId = projectId,
        scanner = "trivy",
        externalId = "CVE-2024-38816",
        location = "pkg:maven/org.x/$id",
        kind = FindingKind.DEPENDENCIES,
        title = "Path traversal",
        url = null,
        firstSeen = time,
        lastSeen = time,
        resolvedAt = null,
        maxSeverity = FindingSeverity.HIGH,
    )
}
