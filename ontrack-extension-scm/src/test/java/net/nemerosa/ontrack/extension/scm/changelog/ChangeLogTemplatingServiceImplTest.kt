package net.nemerosa.ontrack.extension.scm.changelog

import io.mockk.mockk
import net.nemerosa.ontrack.extension.scm.mock.MockCommit
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.structure.BuildFixtures
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectFixtures
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ChangeLogTemplatingServiceImplTest {

    private val service = ChangeLogTemplatingServiceImpl(
        scmChangeLogService = mockk(),
        entityDisplayNameService = mockk(),
        structureService = mockk(),
    )

    private val project = ProjectFixtures.testProject()

    @Test
    fun `Only the subject of a commit message is rendered`() {
        assertEquals(
            "* abcd123 Some commit",
            render(
                """
                    Some commit

                    With a long body explaining at length what the commit does.
                """.trimIndent()
            )
        )
    }

    @Test
    fun `Commit subject is truncated to the maximum length`() {
        assertEquals(
            "* abcd123 ${"a".repeat(99)}…",
            render("a".repeat(120))
        )
    }

    @Test
    fun `Commit subject truncation can be configured`() {
        assertEquals(
            "* abcd123 Some co…",
            render("Some commit which is too long", maxLength = 8)
        )
    }

    @Test
    fun `Commit subject truncation can be disabled, keeping the subject only`() {
        assertEquals(
            "* abcd123 ${"a".repeat(120)}",
            render("a".repeat(120) + "\n\nWith a body", maxLength = 0)
        )
    }

    private fun render(message: String, maxLength: Int = COMMIT_MESSAGE_DEFAULT_MAX_LENGTH): String {
        val from = BuildFixtures.testBuild(name = "1")
        val to = BuildFixtures.testBuild(name = "2").copy(id = ID.of(from.id() + 1))
        val changeLog = SCMChangeLog(
            from = from,
            to = to,
            fromCommit = "abcd000",
            toCommit = "abcd123",
            commits = listOf(message).toCommits(project),
            issues = SCMChangeLogIssues(
                issueServiceConfiguration = mockk(),
                issues = emptyList(),
            ),
        )
        return service.renderChangeLog(
            changeLog = changeLog,
            // OPTIONAL with no issue renders the commits alone
            config = ChangeLogTemplatingServiceConfig(
                commitsOption = ChangeLogTemplatingCommitsOption.OPTIONAL,
                commitsMaxLength = maxLength,
            ),
            suffix = null,
            renderer = PlainEventRenderer.INSTANCE,
        )
    }

    private fun List<String>.toCommits(project: Project): List<SCMDecoratedCommit> =
        map { message ->
            SCMDecoratedCommit(
                project = project,
                commit = MockCommit(
                    message = message,
                    repository = "ontrack",
                    revision = 0L,
                    id = "abcd123",
                )
            )
        }
}
