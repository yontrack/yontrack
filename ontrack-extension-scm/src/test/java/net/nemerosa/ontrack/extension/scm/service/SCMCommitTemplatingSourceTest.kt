package net.nemerosa.ontrack.extension.scm.service

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.extension.scm.changelog.assistants.SCMCommitAssistant
import net.nemerosa.ontrack.extension.scm.changelog.assistants.SCMCommitAssistantMarker
import net.nemerosa.ontrack.extension.scm.changelog.assistants.SCMCommitAssistantService
import net.nemerosa.ontrack.extension.scm.mock.MockCommit
import net.nemerosa.ontrack.model.templating.TemplatingMisconfiguredConfigParamException
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.structure.BranchFixtures
import net.nemerosa.ontrack.model.structure.BuildFixtures
import net.nemerosa.ontrack.model.templating.TemplatingSourceConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SCMCommitTemplatingSourceTest {

    private lateinit var scmCommitTemplatingSource: SCMCommitTemplatingSource
    private lateinit var scmDetector: SCMDetector
    private lateinit var scm: SCMChangeLogEnabled
    private lateinit var scmCommitAssistantService: SCMCommitAssistantService

    @BeforeEach
    fun init() {
        scm = mockk()
        scmDetector = mockk()
        scmCommitAssistantService = mockk()
        scmCommitTemplatingSource = SCMCommitTemplatingSource(
            scmDetector,
            scmCommitAssistantService,
        )
    }

    @Test
    fun `Commit if build has one`() {
        val build = BuildFixtures.testBuild()

        every {
            scm.getBuildCommit(build)
        } returns "0123456789abcdef0123456789abcdef01234567"

        every {
            scmDetector.getSCM(build.project)
        } returns scm

        val value = scmCommitTemplatingSource.render(build, TemplatingSourceConfig(), PlainEventRenderer.INSTANCE)

        assertEquals(
            "0123456789abcdef0123456789abcdef01234567",
            value
        )
    }

    @Test
    fun `No commit if build has none`() {
        val build = BuildFixtures.testBuild()

        every {
            scm.getBuildCommit(build)
        } returns null

        every {
            scmDetector.getSCM(build.project)
        } returns scm

        val value = scmCommitTemplatingSource.render(build, TemplatingSourceConfig(), PlainEventRenderer.INSTANCE)

        assertEquals(
            "",
            value
        )
    }

    @Test
    fun `No commit if project not configured`() {
        val build = BuildFixtures.testBuild()

        every {
            scmDetector.getSCM(build.project)
        } returns null

        val value = scmCommitTemplatingSource.render(build, TemplatingSourceConfig(), PlainEventRenderer.INSTANCE)

        assertEquals(
            "",
            value
        )
    }

    @Test
    fun `No commit if the SCM cannot give one`() {
        val build = BuildFixtures.testBuild()

        every {
            scmDetector.getSCM(build.project)
        } returns mockk<SCM>()

        val value = scmCommitTemplatingSource.render(build, TemplatingSourceConfig(), PlainEventRenderer.INSTANCE)

        assertEquals(
            "",
            value
        )
    }

    @Test
    fun `No commit if not a build`() {
        val branch = BranchFixtures.testBranch()

        val value = scmCommitTemplatingSource.render(branch, TemplatingSourceConfig(), PlainEventRenderer.INSTANCE)

        assertEquals(
            "",
            value
        )
    }

    @Test
    fun `Commit as the explicit id field`() {
        val build = BuildFixtures.testBuild()
        every { scm.getBuildCommit(build) } returns "0123456789abcdef"
        every { scmDetector.getSCM(build.project) } returns scm

        val value = scmCommitTemplatingSource.render(
            build,
            TemplatingSourceConfig.fromMap("field" to "id"),
            PlainEventRenderer.INSTANCE
        )

        assertEquals("0123456789abcdef", value)
    }

    @Test
    fun `Assistants of the commit, comma-separated`() {
        val build = BuildFixtures.testBuild()
        val commit = MockCommit(repository = "ontrack", revision = 1L, id = "0123456789abcdef", message = "Some commit")
        every { scm.getBuildCommit(build) } returns commit.id
        every { scm.getCommit(commit.id) } returns commit
        every { scmDetector.getSCM(build.project) } returns scm
        every { scmCommitAssistantService.getAssistants(commit) } returns listOf(
            SCMCommitAssistant(
                name = "Claude Code",
                markers = listOf(SCMCommitAssistantMarker.CO_AUTHOR),
                sessionLink = "https://claude.ai/code/session_01",
            ),
            SCMCommitAssistant(name = "Copilot", markers = listOf(SCMCommitAssistantMarker.AUTHOR), sessionLink = null),
        )

        val value = scmCommitTemplatingSource.render(
            build,
            TemplatingSourceConfig.fromMap("field" to "assistants"),
            PlainEventRenderer.INSTANCE
        )

        assertEquals("Claude Code, Copilot", value)
    }

    @Test
    fun `No assistants for a commit written without one`() {
        val build = BuildFixtures.testBuild()
        val commit = MockCommit(repository = "ontrack", revision = 1L, id = "0123456789abcdef", message = "Some commit")
        every { scm.getBuildCommit(build) } returns commit.id
        every { scm.getCommit(commit.id) } returns commit
        every { scmDetector.getSCM(build.project) } returns scm
        every { scmCommitAssistantService.getAssistants(commit) } returns emptyList()

        val value = scmCommitTemplatingSource.render(
            build,
            TemplatingSourceConfig.fromMap("field" to "assistants"),
            PlainEventRenderer.INSTANCE
        )

        assertEquals("", value)
    }

    @Test
    fun `No assistants when the commit cannot be read`() {
        val build = BuildFixtures.testBuild()
        every { scm.getBuildCommit(build) } returns "0123456789abcdef"
        every { scm.getCommit("0123456789abcdef") } returns null
        every { scmDetector.getSCM(build.project) } returns scm

        val value = scmCommitTemplatingSource.render(
            build,
            TemplatingSourceConfig.fromMap("field" to "assistants"),
            PlainEventRenderer.INSTANCE
        )

        assertEquals("", value)
    }

    @Test
    fun `No assistants when the build has no commit`() {
        val build = BuildFixtures.testBuild()
        every { scm.getBuildCommit(build) } returns null
        every { scmDetector.getSCM(build.project) } returns scm

        val value = scmCommitTemplatingSource.render(
            build,
            TemplatingSourceConfig.fromMap("field" to "assistants"),
            PlainEventRenderer.INSTANCE
        )

        assertEquals("", value)
    }

    @Test
    fun `Unknown field`() {
        val build = BuildFixtures.testBuild()
        every { scm.getBuildCommit(build) } returns "0123456789abcdef"
        every { scmDetector.getSCM(build.project) } returns scm

        assertFailsWith<TemplatingMisconfiguredConfigParamException> {
            scmCommitTemplatingSource.render(
                build,
                TemplatingSourceConfig.fromMap("field" to "unknown"),
                PlainEventRenderer.INSTANCE
            )
        }
    }

}
