package net.nemerosa.ontrack.extension.scm.changelog

import io.mockk.mockk
import net.nemerosa.ontrack.extension.scm.changelog.assistants.SCMCommitAssistant
import net.nemerosa.ontrack.extension.scm.changelog.assistants.SCMCommitAssistantMarker
import net.nemerosa.ontrack.extension.scm.changelog.assistants.SCMCommitAssistantService
import net.nemerosa.ontrack.extension.scm.mock.MockCommit
import net.nemerosa.ontrack.model.events.EventRenderer
import net.nemerosa.ontrack.model.events.HtmlNotificationEventRenderer
import net.nemerosa.ontrack.model.events.MarkdownEventRenderer
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.structure.BuildFixtures
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectFixtures
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ChangeLogTemplatingServiceImplTest {

    /**
     * Assistants of the test commits, by commit message
     */
    private val assistantsByMessage = mapOf(
        "Claude commit" to listOf(
            SCMCommitAssistant(
                name = "Claude Code",
                markers = listOf(SCMCommitAssistantMarker.CO_AUTHOR, SCMCommitAssistantMarker.SESSION_TRAILER),
                sessionLink = "https://claude.ai/code/session_01",
            )
        ),
        "Codex commit" to listOf(
            SCMCommitAssistant(
                name = "Codex",
                markers = listOf(SCMCommitAssistantMarker.CO_AUTHOR),
                sessionLink = null,
            )
        ),
        "Pair commit" to listOf(
            SCMCommitAssistant(
                name = "Claude Code",
                markers = listOf(SCMCommitAssistantMarker.CO_AUTHOR),
                sessionLink = null,
            ),
            SCMCommitAssistant(
                name = "Copilot",
                markers = listOf(SCMCommitAssistantMarker.AUTHOR),
                sessionLink = null,
            ),
        ),
    )

    private val scmCommitAssistantService = object : SCMCommitAssistantService {
        override fun getAssistants(commit: SCMCommit): List<SCMCommitAssistant> =
            assistantsByMessage[commit.message] ?: emptyList()
    }

    private val service = ChangeLogTemplatingServiceImpl(
        scmChangeLogService = mockk(),
        entityDisplayNameService = mockk(),
        structureService = mockk(),
        scmCommitAssistantService = scmCommitAssistantService,
    )

    private val project = ProjectFixtures.testProject()

    private val markdown = MarkdownEventRenderer(OntrackConfigProperties())
    private val html = HtmlNotificationEventRenderer(OntrackConfigProperties())

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

    @Test
    fun `Assistants are not rendered by default`() {
        assertEquals(
            """
                * abcd123 Claude commit
                * abcd123 Human commit
            """.trimIndent(),
            render(listOf("Claude commit", "Human commit"))
        )
    }

    @Test
    fun `Assistants in plain text`() {
        assertEquals(
            """
                * abcd123 Claude commit (assisted by Claude Code)
                * abcd123 Human commit
                * abcd123 Codex commit (assisted by Codex)
            """.trimIndent(),
            render(
                listOf("Claude commit", "Human commit", "Codex commit"),
                config = config(assistants = true),
            )
        )
    }

    @Test
    fun `Several assistants on a commit`() {
        assertEquals(
            "* abcd123 Pair commit (assisted by Claude Code, Copilot)",
            render(listOf("Pair commit"), config = config(assistants = true))
        )
    }

    @Test
    fun `Assistants in Markdown, with a link to the session`() {
        assertEquals(
            """
                * [abcd123](mock://ontrack/abcd123) Claude commit (assisted by [Claude Code](https://claude.ai/code/session_01))
                * [abcd123](mock://ontrack/abcd123) Codex commit (assisted by Codex)
            """.trimIndent(),
            render(
                listOf("Claude commit", "Codex commit"),
                config = config(assistants = true),
                renderer = markdown,
            )
        )
    }

    @Test
    fun `Assistants in HTML, with a link to the session`() {
        val text = render(
            listOf("Claude commit", "Codex commit"),
            config = config(assistants = true),
            renderer = html,
        )
        assertEquals(
            html.renderList(
                listOf(
                    """<a href="mock://ontrack/abcd123">abcd123</a> Claude commit (assisted by <a href="https://claude.ai/code/session_01">Claude Code</a>)""",
                    """<a href="mock://ontrack/abcd123">abcd123</a> Codex commit (assisted by Codex)""",
                )
            ),
            text
        )
        assertContains(text, """(assisted by <a href="https://claude.ai/code/session_01">Claude Code</a>)""")
    }

    @Test
    fun `Assisted count is not rendered by default`() {
        assertEquals(
            "* abcd123 Claude commit",
            render(listOf("Claude commit"))
        )
    }

    @Test
    fun `Assisted count in plain text`() {
        assertEquals(
            """
                2 of 3 commits assisted
                
                * abcd123 Claude commit
                * abcd123 Human commit
                * abcd123 Codex commit
            """.trimIndent(),
            render(
                listOf("Claude commit", "Human commit", "Codex commit"),
                config = config(assistedCount = true),
            )
        )
    }

    @Test
    fun `Assisted count when no commit is assisted`() {
        assertEquals(
            """
                0 of 2 commits assisted
                
                * abcd123 Human commit
                * abcd123 Another human commit
            """.trimIndent(),
            render(
                listOf("Human commit", "Another human commit"),
                config = config(assistedCount = true),
            )
        )
    }

    @Test
    fun `Assisted count for a single commit`() {
        assertEquals(
            """
                1 of 1 commit assisted
                
                * abcd123 Claude commit
            """.trimIndent(),
            render(listOf("Claude commit"), config = config(assistedCount = true))
        )
    }

    @Test
    fun `Assisted count when the commits are not rendered`() {
        assertEquals(
            "1 of 2 commits assisted",
            render(
                listOf("Claude commit", "Human commit"),
                config = config(assistedCount = true, commitsOption = ChangeLogTemplatingCommitsOption.NONE),
            )
        )
    }

    @Test
    fun `Assisted count is not rendered for a change log without commits`() {
        assertEquals(
            "",
            render(emptyList(), config = config(assistedCount = true))
        )
    }

    @Test
    fun `Assisted count and assistants in Markdown`() {
        assertEquals(
            """
                1 of 2 commits assisted
                
                * [abcd123](mock://ontrack/abcd123) Claude commit (assisted by [Claude Code](https://claude.ai/code/session_01))
                * [abcd123](mock://ontrack/abcd123) Human commit
            """.trimIndent(),
            render(
                listOf("Claude commit", "Human commit"),
                config = config(assistants = true, assistedCount = true),
                renderer = markdown,
            )
        )
    }

    @Test
    fun `Assisted count in HTML`() {
        assertEquals(
            "1 of 2 commits assisted<br/><br/>" + html.renderList(
                listOf(
                    """<a href="mock://ontrack/abcd123">abcd123</a> Claude commit""",
                    """<a href="mock://ontrack/abcd123">abcd123</a> Human commit""",
                )
            ),
            render(
                listOf("Claude commit", "Human commit"),
                config = config(assistedCount = true),
                renderer = html,
            )
        )
    }

    private fun config(
        assistants: Boolean = false,
        assistedCount: Boolean = false,
        // OPTIONAL with no issue renders the commits alone
        commitsOption: ChangeLogTemplatingCommitsOption = ChangeLogTemplatingCommitsOption.OPTIONAL,
        maxLength: Int = COMMIT_MESSAGE_DEFAULT_MAX_LENGTH,
    ) = ChangeLogTemplatingServiceConfig(
        commitsOption = commitsOption,
        commitsMaxLength = maxLength,
        assistants = assistants,
        assistedCount = assistedCount,
    )

    private fun render(message: String, maxLength: Int = COMMIT_MESSAGE_DEFAULT_MAX_LENGTH): String =
        render(listOf(message), config = config(maxLength = maxLength))

    private fun render(
        messages: List<String>,
        config: ChangeLogTemplatingServiceConfig = config(),
        renderer: EventRenderer = PlainEventRenderer.INSTANCE,
    ): String {
        val from = BuildFixtures.testBuild(name = "1")
        val to = BuildFixtures.testBuild(name = "2").copy(id = ID.of(from.id() + 1))
        val changeLog = SCMChangeLog(
            from = from,
            to = to,
            fromCommit = "abcd000",
            toCommit = "abcd123",
            commits = messages.toCommits(project),
            issues = SCMChangeLogIssues(
                issueServiceConfiguration = mockk(),
                issues = emptyList(),
            ),
        )
        return service.renderChangeLog(
            changeLog = changeLog,
            config = config,
            suffix = null,
            renderer = renderer,
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
