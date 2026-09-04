package net.nemerosa.ontrack.extension.scm.changelog

import io.mockk.mockk
import net.nemerosa.ontrack.extension.issues.model.Issue
import net.nemerosa.ontrack.extension.scm.mock.MockCommit
import net.nemerosa.ontrack.extension.scm.mock.MockIssue
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.structure.BuildFixtures
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectFixtures
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SemanticChangelogRenderingServiceImplTest {

    private lateinit var semanticChangelogRenderingService: SemanticChangelogRenderingServiceImpl
    private val semanticChangelogService: SemanticChangelogService = SemanticChangelogServiceImpl()
    private val project = ProjectFixtures.testProject()

    @BeforeEach
    fun init() {
        semanticChangelogRenderingService = SemanticChangelogRenderingServiceImpl(semanticChangelogService)
    }

    @Test
    fun `Default semantic change log`() {
        val changelog = SCMChangeLog(
            from = BuildFixtures.testBuild(),
            to = BuildFixtures.testBuild(),
            fromCommit = "abcd123",
            toCommit = "abcd456",
            commits = listOf(
                "feat(My feature): Some commits for a feature",
                "feat(My feature): Some fixes for a feature",
                "fix: Fixing some bugs",
                "fix: Fixing some CSS"
            ).toCommits(project),
            issues = SCMChangeLogIssues(
                issueServiceConfiguration = mockk(),
                issues = emptyList()
            )
        )

        val text = semanticChangelogRenderingService.render(
            changelog = changelog,
            config = SemanticChangeLogTemplatingServiceConfig(),
            suffix = null,
            renderer = PlainEventRenderer.INSTANCE
        )
        assertEquals(
            """
                Features:
                
                * My feature - Some commits for a feature
                * My feature - Some fixes for a feature
                
                Fixes:
                
                * Fixing some bugs
                * Fixing some CSS
            """.trimIndent().trim(),
            text.trim()
        )
    }

    @Test
    fun `Default semantic changelog with emojis`() {
        val changelog = SCMChangeLog(
            from = BuildFixtures.testBuild(),
            to = BuildFixtures.testBuild(),
            fromCommit = "abcd123",
            toCommit = "abcd456",
            commits = listOf(
                "feat(My feature): Some commits for a feature",
                "feat(My feature): Some fixes for a feature",
                "fix: Fixing some bugs",
                "fix: Fixing some CSS"
            ).toCommits(project),
            issues = SCMChangeLogIssues(
                issueServiceConfiguration = mockk(),
                issues = emptyList()
            )
        )

        val text = semanticChangelogRenderingService.render(
            changelog = changelog,
            config = SemanticChangeLogTemplatingServiceConfig(emojis = true),
            suffix = null,
            renderer = PlainEventRenderer.INSTANCE
        )
        assertEquals(
            """
                ✨ Features:
                
                * My feature - Some commits for a feature
                * My feature - Some fixes for a feature
                
                🐛 Fixes:
                
                * Fixing some bugs
                * Fixing some CSS
            """.trimIndent().trim(),
            text.trim()
        )
    }

    @Test
    fun `Sending a semantic change log on a promotion run with some issues`() {
        val changelog = SCMChangeLog(
            from = BuildFixtures.testBuild(),
            to = BuildFixtures.testBuild(),
            fromCommit = "abcd123",
            toCommit = "abcd456",
            commits = listOf(
                "ISS-21 Some commits for a feature",
                "ISS-21 Some fixes for a feature",
                "ci: Fixing the pipeline",
                "chore: Formatting some code",
                "ISS-22 Fixing some bugs",
                "docs: Updating the readme",
                "ISS-23 Fixing some CSS",
            ).toCommits(project),
            issues = SCMChangeLogIssues(
                issueServiceConfiguration = mockk(),
                issues = mapOf(
                    "ISS-21" to "Some new feature",
                    "ISS-22" to "Some fixes are needed",
                    "ISS-23" to "Some nicer UI",
                ).toIssues(),
            )
        )

        val text = semanticChangelogRenderingService.render(
            changelog = changelog,
            config = SemanticChangeLogTemplatingServiceConfig(issues = true),
            suffix = null,
            renderer = PlainEventRenderer.INSTANCE
        )
        assertEquals(
            """
                Issues:
                
                * ISS-21 Some new feature
                * ISS-22 Some fixes are needed
                * ISS-23 Some nicer UI
                
                CI:
                
                * Fixing the pipeline
                
                Documentation:
                
                * Updating the readme
                
                Misc.:
                
                * Formatting some code
            """.trimIndent().trim(),
            text.trim()
        )
    }

    @Test
    fun `Sending a semantic change log on a promotion run with some issues and excluding ci`() {
        val changelog = SCMChangeLog(
            from = BuildFixtures.testBuild(),
            to = BuildFixtures.testBuild(),
            fromCommit = "abcd123",
            toCommit = "abcd456",
            commits = listOf(
                "ISS-21 Some commits for a feature",
                "ISS-21 Some fixes for a feature",
                "ci: Fixing the pipeline",
                "chore: Formatting some code",
                "ISS-22 Fixing some bugs",
                "docs: Updating the readme",
                "ISS-23 Fixing some CSS",
            ).toCommits(project),
            issues = SCMChangeLogIssues(
                issueServiceConfiguration = mockk(),
                issues = mapOf(
                    "ISS-21" to "Some new feature",
                    "ISS-22" to "Some fixes are needed",
                    "ISS-23" to "Some nicer UI",
                ).toIssues(),
            )
        )

        val text = semanticChangelogRenderingService.render(
            changelog = changelog,
            config = SemanticChangeLogTemplatingServiceConfig(
                issues = true,
                exclude = listOf("ci"),
            ),
            suffix = null,
            renderer = PlainEventRenderer.INSTANCE
        )
        assertEquals(
            """
                Issues:

                * ISS-21 Some new feature
                * ISS-22 Some fixes are needed
                * ISS-23 Some nicer UI

                Documentation:

                * Updating the readme

                Misc.:

                * Formatting some code
            """.trimIndent(),
            text.trim()
        )
    }

    @Test
    fun `Sending a semantic change log on a promotion run with some issues and adapting title`() {
        val changelog = SCMChangeLog(
            from = BuildFixtures.testBuild(),
            to = BuildFixtures.testBuild(),
            fromCommit = "abcd123",
            toCommit = "abcd456",
            commits = listOf(
                "ISS-21 Some commits for a feature",
                "ISS-21 Some fixes for a feature",
                "ci: Fixing the pipeline",
                "chore: Formatting some code",
                "ISS-22 Fixing some bugs",
                "docs: Updating the readme",
                "ISS-23 Fixing some CSS",
            ).toCommits(project),
            issues = SCMChangeLogIssues(
                issueServiceConfiguration = mockk(),
                issues = mapOf(
                    "ISS-21" to "Some new feature",
                    "ISS-22" to "Some fixes are needed",
                    "ISS-23" to "Some nicer UI",
                ).toIssues(),
            )
        )

        val text = semanticChangelogRenderingService.render(
            changelog = changelog,
            config = SemanticChangeLogTemplatingServiceConfig(
                issues = true,
                sections = listOf(
                    SemanticChangeLogSection("ci", "Delivery"),
                    SemanticChangeLogSection("chore", "Other"),
                )
            ),
            suffix = null,
            renderer = PlainEventRenderer.INSTANCE
        )
        assertEquals(
            """
                Issues:

                * ISS-21 Some new feature
                * ISS-22 Some fixes are needed
                * ISS-23 Some nicer UI

                Delivery:

                * Fixing the pipeline

                Documentation:

                * Updating the readme

                Other:

                * Formatting some code
            """.trimIndent(),
            text.trim()
        )
    }

    @Test
    fun `Commit subjects are truncated and their bodies left out`() {
        val changelog = SCMChangeLog(
            from = BuildFixtures.testBuild(),
            to = BuildFixtures.testBuild(),
            fromCommit = "abcd123",
            toCommit = "abcd456",
            commits = listOf(
                "feat(My feature): " + "a".repeat(120),
                "fix: Fixing some bugs\n\nWith a long body explaining at length what the commit does.",
            ).toCommits(project),
            issues = SCMChangeLogIssues(
                issueServiceConfiguration = mockk(),
                issues = emptyList()
            )
        )

        val text = semanticChangelogRenderingService.render(
            changelog = changelog,
            config = SemanticChangeLogTemplatingServiceConfig(),
            suffix = null,
            renderer = PlainEventRenderer.INSTANCE
        )
        assertEquals(
            """
                Features:

                * My feature - ${"a".repeat(99)}…

                Fixes:

                * Fixing some bugs
            """.trimIndent().trim(),
            text.trim()
        )
    }

    @Test
    fun `Commit subject truncation can be disabled`() {
        val changelog = SCMChangeLog(
            from = BuildFixtures.testBuild(),
            to = BuildFixtures.testBuild(),
            fromCommit = "abcd123",
            toCommit = "abcd456",
            commits = listOf(
                "fix: " + "a".repeat(120) + "\n\nWith a body.",
            ).toCommits(project),
            issues = SCMChangeLogIssues(
                issueServiceConfiguration = mockk(),
                issues = emptyList()
            )
        )

        val text = semanticChangelogRenderingService.render(
            changelog = changelog,
            config = SemanticChangeLogTemplatingServiceConfig(commitsMaxLength = 0),
            suffix = null,
            renderer = PlainEventRenderer.INSTANCE
        )
        assertEquals(
            """
                Fixes:

                * ${"a".repeat(120)}
            """.trimIndent().trim(),
            text.trim()
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

    private fun Map<String, String>.toIssues(): List<Issue> =
        map { (key, value) ->
            MockIssue(
                repositoryName = "ontrack",
                key = key,
                message = value,
            )
        }

}
