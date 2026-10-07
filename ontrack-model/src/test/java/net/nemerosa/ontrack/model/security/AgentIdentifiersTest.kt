package net.nemerosa.ontrack.model.security

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentIdentifiersTest {

    @Test
    fun `Valid slugs`() {
        listOf("a", "claude-code", "codex-2", "a".repeat(32)).forEach {
            assertTrue(AgentIdentifiers.isValidSlug(it), "\"$it\" is valid")
        }
    }

    @Test
    fun `Invalid slugs`() {
        listOf("", "Claude", "claude_code", "claude.code", "a b", "a".repeat(33), "codex[agent]").forEach {
            assertFalse(AgentIdentifiers.isValidSlug(it), "\"$it\" is not valid")
        }
    }

    @Test
    fun `Identifier of an agent`() {
        assertEquals("claude-code[agent]", AgentIdentifiers.identifier("claude-code"))
        assertEquals("claude-code", AgentIdentifiers.slug("claude-code[agent]"))
        assertNull(AgentIdentifiers.slug("damien@example.com"))
    }

    @Test
    fun `The longest identifier fits in a signature`() {
        // Signer columns are VARCHAR(40)
        assertTrue(AgentIdentifiers.identifier("a".repeat(32)).length <= 40)
    }

    @Test
    fun `Agent identifiers are told from emails`() {
        assertTrue(AgentIdentifiers.isAgentIdentifier("codex[agent]"))
        assertTrue(AgentIdentifiers.isAgentIdentifier(" codex[agent] "))
        assertFalse(AgentIdentifiers.isAgentIdentifier("codex@openai.com"))
        assertFalse(AgentIdentifiers.isAgentIdentifier("copilot-swe-agent[bot]"))
    }
}
