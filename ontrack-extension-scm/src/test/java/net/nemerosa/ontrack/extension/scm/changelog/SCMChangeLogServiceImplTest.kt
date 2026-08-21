package net.nemerosa.ontrack.extension.scm.changelog

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfigurationRepresentation
import net.nemerosa.ontrack.extension.scm.mock.MockCommit
import net.nemerosa.ontrack.extension.scm.mock.MockIssue
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.model.structure.BranchFixtures
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.BuildFixtures
import net.nemerosa.ontrack.model.structure.StructureService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SCMChangeLogServiceImplTest {

    private val branch = BranchFixtures.testBranch()
    private val from: Build = BuildFixtures.testBuild(branch = branch, name = "1")
    private val to: Build = BuildFixtures.testBuild(branch = branch, name = "2")

    private val configuredIssueService = mockk<ConfiguredIssueService>()
    private val scm = mockk<SCMChangeLogEnabled>()
    private val scmDetector = mockk<SCMDetector>()
    private val structureService = mockk<StructureService>()

    private val service: SCMChangeLogService = SCMChangeLogServiceImpl(scmDetector, structureService)

    private fun setupChangeLog() {
        every { scmDetector.getSCM(branch.project) } returns scm
        every { scm.getBuildCommit(from) } returns "commit-1"
        every { scm.getBuildCommit(to) } returns "commit-2"
        coEvery { scm.getCommits("commit-1", "commit-2") } returns listOf(
            MockCommit(repository = "ontrack", revision = 1L, id = "commit-2", message = "ISS-21 Some feature"),
        )
        every { scm.getConfiguredIssueService() } returns configuredIssueService
        every { configuredIssueService.extractIssueKeysFromMessage(any()) } returns setOf("ISS-21")
        every { configuredIssueService.getIssue("ISS-21") } returns MockIssue(
            repositoryName = "ontrack",
            key = "ISS-21",
            message = "Some new feature",
        )
        every { configuredIssueService.issueServiceConfigurationRepresentation } returns
                IssueServiceConfigurationRepresentation("mock//config", "Config (Mock)", "mock")
    }

    private fun getChangeLog(): SCMChangeLog =
        runBlocking { service.getChangeLog(from = from, to = to) } ?: fail("Could not get a change log")

    @Test
    fun `Issues are not resolved when only the commits are needed`() {
        setupChangeLog()

        val changeLog = getChangeLog()

        assertEquals(
            listOf("ISS-21 Some feature"),
            changeLog.commits.map { it.commit.message },
            "Change log commits"
        )

        verify(exactly = 0) { configuredIssueService.getIssue(any()) }
    }

    @Test
    fun `Issues are resolved when they are accessed`() {
        setupChangeLog()

        val changeLog = getChangeLog()

        assertEquals(
            listOf("ISS-21" to "Some new feature"),
            changeLog.issues?.issues?.map { it.key to it.summary },
            "Change log issues"
        )

        verify(exactly = 1) { configuredIssueService.getIssue("ISS-21") }
    }

    @Test
    fun `Issues are resolved only once`() {
        setupChangeLog()

        val changeLog = getChangeLog()

        repeat(3) { changeLog.issues }

        verify(exactly = 1) { configuredIssueService.getIssue("ISS-21") }
    }
}
