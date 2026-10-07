package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventRenderer
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.HtmlNotificationEventRenderer
import net.nemerosa.ontrack.model.events.MarkdownEventRenderer
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.templating.TemplatingService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * Assistants of the commits in the templated change logs and in the `scmCommit` templating source.
 */
@AsAdminTest
class SCMCommitAssistantsTemplatingIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var mockSCMTester: MockSCMTester

    @Autowired
    private lateinit var templatingService: TemplatingService

    @Autowired
    private lateinit var eventTemplatingService: EventTemplatingService

    @Autowired
    private lateinit var eventFactory: EventFactory

    @Autowired
    private lateinit var markdownEventRenderer: MarkdownEventRenderer

    @Autowired
    private lateinit var htmlNotificationEventRenderer: HtmlNotificationEventRenderer

    @Test
    fun `Build change log renders as before by default`() {
        withAssistedChangeLog { from, to, (human, claude) ->
            assertEquals(
                """
                    * $claude Some fix
                    * $human Some feature
                """.trimIndent(),
                renderBuildChangeLog(from, to, "commitsOption=OPTIONAL")
            )
        }
    }

    @Test
    fun `Build change log with assistants and assisted count in plain text`() {
        withAssistedChangeLog { from, to, (human, claude) ->
            assertEquals(
                """
                    1 of 2 commits assisted
                    
                    * $claude Some fix (assisted by Claude Code)
                    * $human Some feature
                """.trimIndent(),
                renderBuildChangeLog(from, to, "commitsOption=OPTIONAL&assistants=true&assistedCount=true")
            )
        }
    }

    @Test
    fun `Build change log with assistants and assisted count in Markdown`() {
        withAssistedChangeLog { from, to, (human, claude) ->
            assertEquals(
                """
                    1 of 2 commits assisted
                    
                    * [$claude](mock://$repository/$claude) Some fix (assisted by [Claude Code](https://claude.ai/code/session_123))
                    * [$human](mock://$repository/$human) Some feature
                """.trimIndent(),
                renderBuildChangeLog(
                    from, to,
                    "commitsOption=OPTIONAL&assistants=true&assistedCount=true",
                    markdownEventRenderer
                )
            )
        }
    }

    @Test
    fun `Build change log with assistants in HTML`() {
        withAssistedChangeLog { from, to, (human, claude) ->
            assertEquals(
                htmlNotificationEventRenderer.renderList(
                    listOf(
                        """<a href="mock://$repository/$claude">$claude</a> Some fix (assisted by <a href="https://claude.ai/code/session_123">Claude Code</a>)""",
                        """<a href="mock://$repository/$human">$human</a> Some feature""",
                    )
                ),
                renderBuildChangeLog(from, to, "commitsOption=OPTIONAL&assistants=true", htmlNotificationEventRenderer)
            )
        }
    }

    @Test
    fun `Promotion run change log with the assisted count`() {
        withAssistedChangeLog { from, to, (human, claude) ->
            val pl = to.branch.promotionLevel()
            from.promote(pl)
            val run = to.promote(pl)
            val text = eventTemplatingService.render(
                template = $$"${promotionRun.changelog?commitsOption=OPTIONAL&assistedCount=true}",
                event = eventFactory.newPromotionRun(run),
                context = emptyMap(),
                renderer = PlainEventRenderer.INSTANCE,
            )
            assertEquals(
                """
                    1 of 2 commits assisted
                    
                    * $claude Some fix
                    * $human Some feature
                """.trimIndent(),
                text
            )
        }
    }

    @Test
    fun `Assistants of the commit of a build`() {
        withAssistedChangeLog { _, to, _ ->
            assertEquals(
                "Assisted by Claude Code",
                templatingService.render(
                    template = $$"Assisted by ${build.scmCommit?field=assistants}",
                    context = mapOf("build" to to),
                    renderer = PlainEventRenderer.INSTANCE,
                )
            )
        }
    }

    @Test
    fun `No assistants for the commit of a build written without one`() {
        withAssistedChangeLog { from, _, _ ->
            assertEquals(
                "Assisted by ",
                templatingService.render(
                    template = $$"Assisted by ${build.scmCommit?field=assistants}",
                    context = mapOf("build" to from),
                    renderer = PlainEventRenderer.INSTANCE,
                )
            )
        }
    }

    private fun renderBuildChangeLog(
        from: Build,
        to: Build,
        options: String,
        renderer: EventRenderer = PlainEventRenderer.INSTANCE,
    ): String = templatingService.render(
        template = "\${build.changelog?from=${from.id}&$options}",
        context = mapOf("build" to to),
        renderer = renderer,
    )

    /**
     * Name of the mock repository of the current test
     */
    private lateinit var repository: String

    /**
     * Change log of two commits: a human one, then one co-authored by Claude Code with a session link.
     *
     * @param code Gets the boundaries of the change log and the IDs of the human and assisted commits
     */
    private fun withAssistedChangeLog(code: (from: Build, to: Build, commits: List<String>) -> Unit) {
        withCleanSettings<AgentMarkersSettings> {
            mockSCMTester.withMockSCMRepository {
                repository = repositoryName
                project {
                    branch {
                        configureMockSCMBranch()
                        val from = build {
                            withRepositoryCommit("Before the change log")
                        }
                        lateinit var human: String
                        lateinit var claude: String
                        val to = build {
                            human = withRepositoryCommit("Some feature", property = false)
                            claude = withRepositoryCommit(
                                """
                                    Some fix

                                    Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
                                    Claude-Session: https://claude.ai/code/session_123
                                """.trimIndent()
                            )
                        }
                        code(from, to, listOf(human, claude))
                    }
                }
            }
        }
    }
}
