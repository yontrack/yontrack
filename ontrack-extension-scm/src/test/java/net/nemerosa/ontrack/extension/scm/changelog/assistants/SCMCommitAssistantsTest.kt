package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.extension.scm.changelog.SimpleSCMCommit
import net.nemerosa.ontrack.extension.scm.changelog.assistants.SCMCommitAssistantMarker.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class SCMCommitAssistantsTest {

    private val builtIns = AgentMarkerRules(AgentMarkersSettings())

    private fun commit(
        message: String,
        author: String = "Damien",
        authorEmail: String? = "damien@example.com",
        committer: String? = null,
        authorLogin: String? = null,
        committerLogin: String? = null,
    ) = SimpleSCMCommit(
        id = "0123456789abcdef",
        shortId = "0123456",
        author = author,
        authorEmail = authorEmail,
        timestamp = LocalDateTime.of(2026, 10, 7, 12, 0),
        message = message,
        link = "https://example.com/commit/0123456789abcdef",
        committer = committer,
        authorLogin = authorLogin,
        committerLogin = committerLogin,
    )

    private fun assistants(
        message: String,
        rules: AgentMarkerRules = builtIns,
        author: String = "Damien",
        authorEmail: String? = "damien@example.com",
        committer: String? = null,
        authorLogin: String? = null,
        committerLogin: String? = null,
    ) = commitAssistants(
        commit(message, author, authorEmail, committer, authorLogin, committerLogin),
        rules,
    )

    private fun assistant(name: String, vararg markers: SCMCommitAssistantMarker, sessionLink: String? = null) =
        SCMCommitAssistant(name = name, markers = markers.toList(), sessionLink = sessionLink)

    // Trailer block

    @Test
    fun `Trailers of the last paragraph`() {
        assertEquals(
            listOf("Refs" to "#12", "Co-Authored-By" to "Claude <noreply@anthropic.com>"),
            commitTrailers(
                """
                    Subject

                    Some body.

                    Refs: #12
                    Co-Authored-By: Claude <noreply@anthropic.com>
                """.trimIndent()
            )
        )
    }

    @Test
    fun `No trailers in a subject alone`() {
        assertEquals(emptyList(), commitTrailers("Co-Authored-By: Claude <noreply@anthropic.com>"))
    }

    @Test
    fun `Trailers with Windows line endings`() {
        assertEquals(
            listOf("Co-Authored-By" to "Claude <noreply@anthropic.com>"),
            commitTrailers("Subject\r\n\r\nBody\r\n\r\nCo-Authored-By: Claude <noreply@anthropic.com>\r\n")
        )
    }

    @Test
    fun `Trailing blank lines are ignored`() {
        assertEquals(
            listOf("Assisted-by" to "Codex"),
            commitTrailers("Subject\n\nAssisted-by: Codex\n\n\n")
        )
    }

    // Plain commits

    @Test
    fun `No assistant on a plain commit`() {
        assertEquals(emptyList(), assistants("Some fix\n\nWith a body."))
    }

    // Co-Authored-By

    @Test
    fun `Claude Code from its co-author trailer`() {
        assertEquals(
            listOf(assistant("Claude Code", CO_AUTHOR)),
            assistants("Some fix\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>")
        )
    }

    @Test
    fun `Codex from its co-author trailer`() {
        assertEquals(
            listOf(assistant("Codex", CO_AUTHOR)),
            assistants("Some fix\n\nCo-authored-by: Codex <codex@openai.com>")
        )
    }

    @Test
    fun `Copilot from its co-author trailer`() {
        assertEquals(
            listOf(assistant("Copilot", CO_AUTHOR)),
            assistants("Some fix\n\nCo-authored-by: Copilot <copilot@github.com>")
        )
    }

    @Test
    fun `Trailer keys are case-insensitive`() {
        assertEquals(
            listOf(assistant("Claude Code", CO_AUTHOR)),
            assistants("Some fix\n\nco-authored-BY: Claude <NoReply@Anthropic.com>")
        )
    }

    @Test
    fun `A human co-author is not an assistant`() {
        assertEquals(
            emptyList(),
            assistants("Some fix\n\nCo-Authored-By: Jane Doe <jane@example.com>")
        )
    }

    @Test
    fun `A lookalike email is not an assistant`() {
        assertEquals(
            emptyList(),
            assistants(
                """
                    Some fix

                    Co-Authored-By: Claude <noreply@anthropic.com.example.com>
                    Co-Authored-By: Claude <fake-noreply@anthropic.com>
                    Co-authored-by: Codex <codex@openai.co>
                """.trimIndent()
            )
        )
    }

    @Test
    fun `A trailer outside the last paragraph is not read`() {
        assertEquals(
            emptyList(),
            assistants(
                """
                    Some fix

                    Co-Authored-By: Claude <noreply@anthropic.com>

                    The previous line is prose, not a trailer.
                """.trimIndent()
            )
        )
    }

    @Test
    fun `A co-author trailer in a subject alone is not read`() {
        assertEquals(emptyList(), assistants("Co-Authored-By: Claude <noreply@anthropic.com>"))
    }

    @Test
    fun `Windows line endings`() {
        assertEquals(
            listOf(assistant("Claude Code", CO_AUTHOR)),
            assistants("Some fix\r\n\r\nSome body\r\n\r\nCo-Authored-By: Claude <noreply@anthropic.com>\r\n")
        )
    }

    // Assisted-by

    @Test
    fun `Assisted-by names the assistant`() {
        assertEquals(
            listOf(assistant("Gemini CLI", ASSISTED_BY)),
            assistants("Some fix\n\nAssisted-by:   Gemini CLI  ")
        )
    }

    @Test
    fun `Kernel-style Assisted-by keeps the name before the model`() {
        assertEquals(
            listOf(assistant("Claude", ASSISTED_BY)),
            assistants("Some fix\n\nAssisted-by: Claude:claude-opus-5-5 coccinelle\nSigned-off-by: Jane Doe <jane@example.com>")
        )
    }

    @Test
    fun `An empty Assisted-by is ignored`() {
        assertEquals(emptyList(), assistants("Some fix\n\nAssisted-by:"))
    }

    // Claude-Session

    @Test
    fun `Claude-Session gives Claude Code and its session link`() {
        assertEquals(
            listOf(assistant("Claude Code", SESSION_TRAILER, sessionLink = "https://claude.ai/code/session_123")),
            assistants("Some fix\n\nClaude-Session: https://claude.ai/code/session_123")
        )
    }

    // Logins and author

    @Test
    fun `Copilot from its author email`() {
        assertEquals(
            listOf(assistant("Copilot", AUTHOR)),
            assistants("Some fix", author = "Copilot", authorEmail = "copilot@github.com")
        )
    }

    @Test
    fun `A lookalike author email is not an assistant`() {
        assertEquals(
            emptyList(),
            assistants("Some fix", author = "Copilot", authorEmail = "copilot@github.com.example.com")
        )
    }

    @Test
    fun `Copilot from its author login`() {
        assertEquals(
            listOf(assistant("Copilot", AUTHOR)),
            assistants("Some fix", author = "Jane", authorLogin = "copilot-swe-agent[bot]")
        )
    }

    @Test
    fun `Copilot from its author name as git records it`() {
        assertEquals(
            listOf(assistant("Copilot", AUTHOR)),
            assistants(
                "Some fix",
                author = "copilot-swe-agent[bot]",
                authorEmail = "198982749+Copilot@users.noreply.github.com"
            )
        )
    }

    @Test
    fun `Devin from its committer login`() {
        assertEquals(
            listOf(assistant("Devin", COMMITTER)),
            assistants("Some fix", committerLogin = "devin-ai-integration[bot]")
        )
    }

    @Test
    fun `Devin from its committer name`() {
        assertEquals(
            listOf(assistant("Devin", COMMITTER)),
            assistants("Some fix", committer = "devin-ai-integration[bot]")
        )
    }

    @Test
    fun `A lookalike login is not an assistant`() {
        assertEquals(
            emptyList(),
            assistants("Some fix", author = "copilot-swe-agent", authorLogin = "devin-ai-integration")
        )
    }

    // Merge

    @Test
    fun `One assistant per commit, its markers merged, the first session link wins`() {
        assertEquals(
            listOf(
                assistant(
                    "Claude Code",
                    CO_AUTHOR,
                    SESSION_TRAILER,
                    ASSISTED_BY,
                    sessionLink = "https://claude.ai/code/session_1"
                ),
                assistant("Copilot", CO_AUTHOR, AUTHOR, COMMITTER),
            ),
            assistants(
                """
                    Some fix

                    Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
                    Claude-Session: https://claude.ai/code/session_1
                    Co-Authored-By: Claude Sonnet <noreply@anthropic.com>
                    Claude-Session: https://claude.ai/code/session_2
                    Assisted-by: Claude Code
                    Co-authored-by: Copilot <copilot@github.com>
                """.trimIndent(),
                authorEmail = "copilot@github.com",
                committerLogin = "copilot-swe-agent[bot]",
            )
        )
    }

    @Test
    fun `Assistants are merged on their name whatever its case`() {
        assertEquals(
            listOf(assistant("Codex", CO_AUTHOR, ASSISTED_BY)),
            assistants("Some fix\n\nCo-authored-by: Codex <codex@openai.com>\nAssisted-by: codex:gpt-5")
        )
    }

    // Patterns

    @Test
    fun `Co-author email pattern`() {
        val rules = AgentMarkerRules(
            AgentMarkersSettings(
                patterns = listOf(
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.CO_AUTHOR_EMAIL, ".*-bot@acme\\.com"),
                )
            )
        )
        assertEquals(
            listOf(assistant("Acme Bot", CO_AUTHOR)),
            assistants("Some fix\n\nCo-Authored-By: Review <review-bot@ACME.com>", rules = rules)
        )
        assertEquals(
            emptyList(),
            assistants("Some fix\n\nCo-Authored-By: Jane <jane@acme.com>", rules = rules)
        )
    }

    @Test
    fun `Trailer pattern`() {
        val rules = AgentMarkerRules(
            AgentMarkersSettings(
                patterns = listOf(
                    AgentMarkerPattern("Acme Agent", AgentMarkerPatternType.TRAILER, "Acme-Agent-Run"),
                )
            )
        )
        assertEquals(
            listOf(assistant("Acme Agent", TRAILER)),
            assistants("Some fix\n\nacme-agent-run: 1234", rules = rules)
        )
        assertEquals(
            emptyList(),
            assistants("Some fix\n\nAcme-Agent-Run-Id: 1234", rules = rules)
        )
    }

    @Test
    fun `Author email pattern`() {
        val rules = AgentMarkerRules(
            AgentMarkersSettings(
                patterns = listOf(
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.AUTHOR_EMAIL, "bot\\+.*@acme\\.com"),
                )
            )
        )
        assertEquals(
            listOf(assistant("Acme Bot", AUTHOR)),
            assistants("Some fix", authorEmail = "bot+fixer@acme.com", rules = rules)
        )
        assertEquals(
            emptyList(),
            assistants("Some fix", authorEmail = "jane@acme.com", rules = rules)
        )
    }

    @Test
    fun `Login pattern`() {
        val rules = AgentMarkerRules(
            AgentMarkersSettings(
                patterns = listOf(
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.LOGIN, "acme-bot"),
                )
            )
        )
        assertEquals(
            listOf(assistant("Acme Bot", AUTHOR, COMMITTER)),
            assistants("Some fix", authorLogin = "acme-bot", committer = "acme-bot", rules = rules)
        )
        assertEquals(
            emptyList(),
            assistants("Some fix", authorLogin = "acme-bot-2", rules = rules)
        )
    }

    @Test
    fun `Patterns add to the built-ins`() {
        val rules = AgentMarkerRules(
            AgentMarkersSettings(
                patterns = listOf(
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.LOGIN, "acme-bot"),
                )
            )
        )
        assertEquals(
            listOf(assistant("Claude Code", CO_AUTHOR), assistant("Acme Bot", AUTHOR)),
            assistants(
                "Some fix\n\nCo-Authored-By: Claude <noreply@anthropic.com>",
                author = "acme-bot",
                rules = rules
            )
        )
    }

    @Test
    fun `Built-ins switched off`() {
        val rules = AgentMarkerRules(
            AgentMarkersSettings(
                builtInConventions = false,
                patterns = listOf(
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.LOGIN, "acme-bot"),
                )
            )
        )
        assertEquals(
            listOf(assistant("Acme Bot", AUTHOR)),
            assistants(
                """
                    Some fix

                    Co-Authored-By: Claude <noreply@anthropic.com>
                    Assisted-by: Codex
                    Claude-Session: https://claude.ai/code/session_1
                """.trimIndent(),
                author = "acme-bot",
                authorEmail = "copilot@github.com",
                committerLogin = "devin-ai-integration[bot]",
                rules = rules,
            )
        )
    }

    // Validation of the patterns

    @Test
    fun `Valid patterns`() {
        assertEquals(
            emptyList(),
            validateAgentMarkerPatterns(
                listOf(
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.CO_AUTHOR_EMAIL, ".*@acme\\.com"),
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.TRAILER, "Acme-Agent"),
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.AUTHOR_EMAIL, "bot@acme\\.com"),
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.LOGIN, "acme-bot[bot]"),
                )
            )
        )
    }

    @Test
    fun `Invalid patterns`() {
        assertEquals(
            listOf(
                "Pattern #1 (Acme Bot): invalid regular expression `*@acme.com`",
                "Pattern #2 (Acme Bot): invalid regular expression `bot[`",
                "Pattern #3: the name is required",
                "Pattern #4 (Acme Bot): the value is required",
                "Pattern #5 (Acme Bot): `Acme Agent` is not a trailer key",
            ),
            validateAgentMarkerPatterns(
                listOf(
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.CO_AUTHOR_EMAIL, "*@acme.com"),
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.AUTHOR_EMAIL, "bot["),
                    AgentMarkerPattern(" ", AgentMarkerPatternType.LOGIN, "acme-bot"),
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.LOGIN, ""),
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.TRAILER, "Acme Agent"),
                )
            )
        )
    }

    @Test
    fun `Invalid patterns are skipped by the rules`() {
        val rules = AgentMarkerRules(
            AgentMarkersSettings(
                patterns = listOf(
                    AgentMarkerPattern("Broken", AgentMarkerPatternType.AUTHOR_EMAIL, "bot["),
                    AgentMarkerPattern("Acme Bot", AgentMarkerPatternType.AUTHOR_EMAIL, "bot@acme\\.com"),
                )
            )
        )
        assertEquals(
            listOf(assistant("Acme Bot", AUTHOR)),
            assistants("Some fix", authorEmail = "bot@acme.com", rules = rules)
        )
    }
}
