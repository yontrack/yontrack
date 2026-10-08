package net.nemerosa.ontrack.extension.notifications.subscriptions

import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.structure.NameDescription.Companion.nd
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class EventExtensionsTest {

    private val project = Project.of(nd("project", "")).withId(ID.of(1))
    private val branch = Branch.of(project, nd("main", "")).withId(ID.of(1))
    private val promotionLevel = PromotionLevel.of(branch, nd("GOLD", "")).withId(ID.of(1))
    private val build = Build.of(branch, nd("1", ""), Signature.of("test")).withId(ID.of(1))
    private val run = PromotionRun.of(build, promotionLevel, Signature.of("test"), "").withId(ID.of(1))

    private fun event(signature: Signature? = Signature.of("test")) = Event.of(EventFactory.NEW_PROMOTION_RUN)
        .with(project)
        .with(branch)
        .with(promotionLevel)
        .with(build)
        .with(run)
        .with(signature)
        .build()

    val event = event()

    private val agentEvent = event(
        Signature.of("claude[agent]").withActor(
            SignatureActor(
                agent = "claude[agent]",
                displayName = "Claude",
                tool = "Claude Code",
                owner = "damien@yontrack.test",
            )
        )
    )

    @Test
    fun `Matching events on one keyword`() {
        assertTrue(event.matchesKeywords("main"))
        assertTrue(event.matchesKeywords("GOLD"))
        assertFalse(event.matchesKeywords("release"))
        assertFalse(event.matchesKeywords("SILVER"))
    }

    @Test
    fun `Matching events on two keyword`() {
        assertTrue(event.matchesKeywords("GOLD main"))
        assertFalse(event.matchesKeywords("GOLD release"))
    }

    @Test
    fun `Matching events on null keywords`() {
        assertTrue(event.matchesKeywords(null))
    }

    @Test
    fun `Matching events on empty keywords`() {
        assertTrue(event.matchesKeywords(""))
    }

    @Test
    fun `Extra spaces between keywords are ignored`() {
        assertTrue(event.matchesKeywords("  GOLD   main "))
    }

    @Test
    fun `actor-agent matches the events of an agent only`() {
        assertTrue(agentEvent.matchesKeywords("actor:agent"))
        assertFalse(event.matchesKeywords("actor:agent"))
    }

    @Test
    fun `actor-human matches the events of a person only`() {
        assertTrue(event.matchesKeywords("actor:human"))
        assertFalse(agentEvent.matchesKeywords("actor:human"))
    }

    @Test
    fun `actor-human matches an event without a signature`() {
        assertTrue(event(signature = null).matchesKeywords("actor:human"))
        assertFalse(event(signature = null).matchesKeywords("actor:agent"))
    }

    @Test
    fun `actor keywords are case-insensitive`() {
        assertTrue(agentEvent.matchesKeywords("Actor:Agent"))
        assertTrue(event.matchesKeywords("ACTOR:HUMAN"))
    }

    @Test
    fun `An unknown actor kind matches nothing`() {
        assertFalse(event.matchesKeywords("actor:robot"))
        assertFalse(agentEvent.matchesKeywords("actor:robot"))
    }

    @Test
    fun `agent keyword with the identifier of the agent`() {
        assertTrue(agentEvent.matchesKeywords("agent:claude[agent]"))
        assertTrue(agentEvent.matchesKeywords("agent:Claude[Agent]"))
        assertFalse(agentEvent.matchesKeywords("agent:codex[agent]"))
        assertFalse(event.matchesKeywords("agent:claude[agent]"))
    }

    @Test
    fun `agent keyword with the slug of the agent`() {
        assertTrue(agentEvent.matchesKeywords("agent:claude"))
        assertTrue(agentEvent.matchesKeywords("agent:CLAUDE"))
        assertFalse(agentEvent.matchesKeywords("agent:codex"))
        assertFalse(event.matchesKeywords("agent:claude"))
    }

    @Test
    fun `The actor is read from the actor of the event, never from its values`() {
        val agentNamedEvent = Event.of(EventFactory.NEW_PROMOTION_RUN)
            .with(project)
            .with("kind", "actor:agent")
            .with("who", "agent:claude")
            .with(Signature.of("test"))
            .build()
        assertFalse(agentNamedEvent.matchesKeywords("actor:agent"))
        assertFalse(agentNamedEvent.matchesKeywords("agent:claude"))
        assertTrue(agentNamedEvent.matchesKeywords("actor:human"))
    }

    @Test
    fun `Actor keywords are combined with plain keywords`() {
        assertTrue(agentEvent.matchesKeywords("GOLD actor:agent"))
        assertTrue(agentEvent.matchesKeywords("agent:claude main GOLD"))
        assertFalse(agentEvent.matchesKeywords("SILVER actor:agent"))
        assertFalse(agentEvent.matchesKeywords("GOLD actor:human"))
        assertTrue(event.matchesKeywords("GOLD actor:human"))
        assertFalse(event.matchesKeywords("GOLD actor:agent"))
    }

}
