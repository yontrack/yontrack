package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.model.security.ActorAgent
import net.nemerosa.ontrack.model.security.ActorAgentSession
import net.nemerosa.ontrack.model.security.ActorVia
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class SignatureActorTest {

    private val agent = ActorAgent(
        name = "claude[agent]",
        displayName = "Claude",
        tool = "Claude Code",
        owner = "damien@yontrack.test",
    )

    private val session = ActorAgentSession(id = "session-1", link = "https://claude.ai/code/session-1")

    private val agentActor = Actor(
        account = "claude[agent]",
        via = ActorVia.TOKEN,
        tokenName = "ci",
        agent = agent,
        agentSession = session,
    )

    private val signatureActor = SignatureActor(
        agent = "claude[agent]",
        displayName = "Claude",
        tool = "Claude Code",
        owner = "damien@yontrack.test",
        session = session,
    )

    @Test
    fun `No signature actor for a person`() {
        assertNull(SignatureActor.of(Actor(account = "damien@yontrack.test", via = ActorVia.UI)))
    }

    @Test
    fun `No signature actor without an actor`() {
        assertNull(SignatureActor.of(null))
    }

    @Test
    fun `Signature actor of an agent`() {
        assertEquals(signatureActor, SignatureActor.of(agentActor))
    }

    @Test
    fun `Signature actor of the system acting on behalf of an agent`() {
        assertEquals(signatureActor, SignatureActor.of(agentActor.runAs("github-ingestion")))
    }

    @Test
    fun `No signature actor for the system acting on behalf of a person`() {
        assertNull(SignatureActor.of(Actor(account = "damien@yontrack.test", via = ActorVia.UI).runAs("auto-promotion")))
    }

    @Test
    fun `JSON form of the signature actor, as stored in the ACTOR column`() {
        assertEquals(
            """{"kind":"agent","agent":"claude[agent]","displayName":"Claude","tool":"Claude Code","owner":"damien@yontrack.test","session":{"id":"session-1","link":"https://claude.ai/code/session-1"}}""".parseAsJson(),
            signatureActor.asJson(),
        )
    }

    @Test
    fun `JSON form of the signature actor without tool nor session`() {
        assertEquals(
            """{"kind":"agent","agent":"claude[agent]","displayName":"Claude","owner":"damien@yontrack.test"}""".parseAsJson(),
            signatureActor.copy(tool = null, session = null).asJson(),
        )
    }

    @Test
    fun `JSON form of the signature actor reads back`() {
        assertEquals(signatureActor, signatureActor.asJson().parse<SignatureActor>())
    }

    @Test
    fun `JSON form of a person's signature is unchanged`() {
        val signature = Signature.of(LocalDateTime.of(2026, 10, 7, 12, 0), "damien@yontrack.test")
        assertEquals(
            setOf("time", "user"),
            signature.asJson().propertyNames().toSet(),
        )
    }

    @Test
    fun `Actor is part of the equality of signatures`() {
        val signature = Signature.of(LocalDateTime.of(2026, 10, 7, 12, 0), "claude[agent]")
        assertNotEquals(signature, signature.withActor(signatureActor))
        assertEquals(signature.withActor(signatureActor), signature.withActor(signatureActor))
    }

    @Test
    fun `Changing the time keeps the actor`() {
        val signature = Signature.of(LocalDateTime.of(2026, 10, 7, 12, 0), "claude[agent]").withActor(signatureActor)
        assertEquals(signatureActor, signature.withTime(LocalDateTime.of(2026, 10, 8, 12, 0)).actor)
        assertEquals(signatureActor, signature.truncate().actor)
    }
}
