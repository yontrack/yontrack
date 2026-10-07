package net.nemerosa.ontrack.model.security

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentSessionHeadersTest {

    @Test
    fun `No header gives no session`() {
        assertNull(AgentSessionHeaders(id = null, link = null).parse())
    }

    @Test
    fun `Session with a link`() {
        assertEquals(
            ActorAgentSession(id = "session-1", link = "https://claude.ai/code/session-1"),
            AgentSessionHeaders(id = "session-1", link = "https://claude.ai/code/session-1").parse(),
        )
    }

    @Test
    fun `Session without a link`() {
        assertEquals(
            ActorAgentSession(id = "session-1"),
            AgentSessionHeaders(id = "session-1", link = null).parse(),
        )
    }

    @Test
    fun `Values are trimmed`() {
        assertEquals(
            ActorAgentSession(id = "session-1", link = "https://claude.ai/code/session-1"),
            AgentSessionHeaders(id = "  session-1 ", link = " https://claude.ai/code/session-1  ").parse(),
        )
    }

    @Test
    fun `A blank session gives no session`() {
        assertNull(AgentSessionHeaders(id = "   ", link = "https://claude.ai/code/session-1").parse())
    }

    @Test
    fun `A link without a session gives no session`() {
        assertNull(AgentSessionHeaders(id = null, link = "https://claude.ai/code/session-1").parse())
    }

    @Test
    fun `A session of 255 characters is kept`() {
        val id = "s".repeat(255)
        assertEquals(ActorAgentSession(id = id), AgentSessionHeaders(id = id, link = null).parse())
    }

    @Test
    fun `A session longer than 255 characters is dropped`() {
        assertNull(AgentSessionHeaders(id = "s".repeat(256), link = "https://claude.ai/code/x").parse())
    }

    @Test
    fun `An http link is dropped and the session kept`() {
        assertEquals(
            ActorAgentSession(id = "session-1"),
            AgentSessionHeaders(id = "session-1", link = "http://claude.ai/code/session-1").parse(),
        )
    }

    @Test
    fun `A relative link is dropped and the session kept`() {
        assertEquals(
            ActorAgentSession(id = "session-1"),
            AgentSessionHeaders(id = "session-1", link = "/code/session-1").parse(),
        )
    }

    @Test
    fun `A malformed link is dropped and the session kept`() {
        assertEquals(
            ActorAgentSession(id = "session-1"),
            AgentSessionHeaders(id = "session-1", link = "https://claude ai/code session").parse(),
        )
    }

    @Test
    fun `A javascript link is dropped and the session kept`() {
        assertEquals(
            ActorAgentSession(id = "session-1"),
            AgentSessionHeaders(id = "session-1", link = "javascript:alert(1)").parse(),
        )
    }

    @Test
    fun `A link of 1000 characters is kept`() {
        val prefix = "https://claude.ai/"
        val link = prefix + "x".repeat(1000 - prefix.length)
        assertEquals(
            ActorAgentSession(id = "session-1", link = link),
            AgentSessionHeaders(id = "session-1", link = link).parse(),
        )
    }

    @Test
    fun `A link longer than 1000 characters is dropped and the session kept`() {
        val prefix = "https://claude.ai/"
        val link = prefix + "x".repeat(1001 - prefix.length)
        assertEquals(
            ActorAgentSession(id = "session-1"),
            AgentSessionHeaders(id = "session-1", link = link).parse(),
        )
    }
}
