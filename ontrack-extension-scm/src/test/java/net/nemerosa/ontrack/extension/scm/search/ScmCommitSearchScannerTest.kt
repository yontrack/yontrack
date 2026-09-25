package net.nemerosa.ontrack.extension.scm.search

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommitFilter
import net.nemerosa.ontrack.extension.scm.mock.MockCommit
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.scm.service.SCMDetector
import net.nemerosa.ontrack.model.structure.ProjectFixtures
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScmCommitSearchScannerTest {

    private val project = ProjectFixtures.testProject()
    private val scmDetector = mockk<SCMDetector>()
    private val scanner = ScmCommitSearchScanner(scmDetector)

    private val commits = listOf(
        MockCommit(repository = "repo", revision = 1L, id = "c1", message = "ISS-1 First"),
        MockCommit(repository = "repo", revision = 2L, id = "c2", message = "ISS-2 Second"),
    )

    /**
     * SCM returning all the commits whatever the filter, and remembering the filter
     */
    private fun scm(sinceSupported: Boolean, issueService: ConfiguredIssueService? = null): Pair<SCMChangeLogEnabled, () -> SCMCommitFilter> {
        val filter = slot<SCMCommitFilter>()
        val code = slot<(SCMCommit) -> Unit>()
        val scm = mockk<SCMChangeLogEnabled>()
        every { scm.commitsSinceSupported } returns sinceSupported
        every { scm.getConfiguredIssueService() } returns issueService
        every { scm.forAllCommits(project, capture(filter), capture(code)) } answers {
            commits.forEach { code.captured(it) }
        }
        every { scmDetector.getSCM(project) } returns scm
        return scm to { filter.captured }
    }

    @Test
    fun `No scan for a project without a SCM able to list its commits`() {
        every { scmDetector.getSCM(project) } returns mockk<SCM>()
        assertNull(scanner.scan(project, sinceCommit = null) {})
    }

    @Test
    fun `A SCM which can list the commits after a given one gets it`() {
        val (_, filter) = scm(sinceSupported = true)
        val scan = scanner.scan(project, sinceCommit = "c0") {}
        assertEquals("c0", filter().sinceCommit)
        assertTrue(scan?.incremental ?: false)
    }

    @Test
    fun `A SCM which cannot list the commits after a given one is scanned fully`() {
        val (_, filter) = scm(sinceSupported = false)
        val scan = scanner.scan(project, sinceCommit = "c0") {}
        assertNull(filter().sinceCommit)
        assertFalse(scan?.incremental ?: true)
    }

    @Test
    fun `The scan returns the number of commits, the last one and the issues`() {
        val issueService = mockk<ConfiguredIssueService>()
        every { issueService.extractIssueKeysFromMessage(any()) } answers {
            setOf(firstArg<String>().substringBefore(" "))
        }
        scm(sinceSupported = true, issueService = issueService)
        val scanned = mutableListOf<String>()
        val scan = scanner.scan(project, sinceCommit = null) { scanned += it.id }
        assertEquals(listOf("c1", "c2"), scanned)
        assertEquals(2, scan?.commits)
        assertEquals("c2", scan?.lastCommit)
        assertEquals(setOf("ISS-1", "ISS-2"), scan?.issueKeys)
    }

    @Test
    fun `Commit messages are truncated to 2 KB without cutting a character`() {
        assertEquals("short", ScmCommitSearchExtension.truncateText("short"))
        assertEquals("a".repeat(2048), ScmCommitSearchExtension.truncateText("a".repeat(3000)))
        // 2-byte characters, cut on a character boundary
        assertEquals("é".repeat(1024), ScmCommitSearchExtension.truncateText("é".repeat(1500)))
        // One byte, then 2-byte characters: the character straddling the limit is dropped
        assertEquals("a" + "é".repeat(1023), ScmCommitSearchExtension.truncateText("a" + "é".repeat(1500)))
        // NUL characters cannot be stored
        assertEquals("ab", ScmCommitSearchExtension.truncateText("a\u0000b"))
    }

}
