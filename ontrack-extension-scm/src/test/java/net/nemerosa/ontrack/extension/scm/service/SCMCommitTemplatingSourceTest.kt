package net.nemerosa.ontrack.extension.scm.service

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.structure.BranchFixtures
import net.nemerosa.ontrack.model.structure.BuildFixtures
import net.nemerosa.ontrack.model.templating.TemplatingSourceConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SCMCommitTemplatingSourceTest {

    private lateinit var scmCommitTemplatingSource: SCMCommitTemplatingSource
    private lateinit var scmDetector: SCMDetector
    private lateinit var scm: SCMChangeLogEnabled

    @BeforeEach
    fun init() {
        scm = mockk()
        scmDetector = mockk()
        scmCommitTemplatingSource = SCMCommitTemplatingSource(
            scmDetector,
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

}
