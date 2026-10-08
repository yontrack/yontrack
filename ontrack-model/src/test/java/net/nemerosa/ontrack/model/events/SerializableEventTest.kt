package net.nemerosa.ontrack.model.events

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.security.ActorAgentSession
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.ProjectFixtures
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.model.structure.SignatureActor
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SerializableEventTest {

    @Test
    fun `Find value`() {
        val event = MockEventType.serializedMockEvent("Some text")
        assertEquals(
            null,
            event.findValue("xxx")
        )
        assertEquals(
            "Some text",
            event.findValue(MockEventType.EVENT_MOCK)
        )
    }

    @Test
    fun `With value`() {
        val event = MockEventType.serializedMockEvent("Some text")
            .withValue("test", "Some value")
        assertEquals(
            "Some value",
            event.findValue("test")
        )
    }

    @Test
    fun `With entity id`() {
        val project = ProjectFixtures.testProject()
        val event = MockEventType.serializedMockEvent("Some text")
            .withEntity(project)
        assertEquals(
            project.id(),
            event.findEntityId(ProjectEntityType.PROJECT)
        )
    }

    @Test
    fun `The actor of the signature survives the serialization of the event for its dispatch`() {
        val actor = SignatureActor(
            agent = "claude[agent]",
            displayName = "Claude",
            tool = "Claude Code",
            owner = "damien@yontrack.test",
            session = ActorAgentSession(id = "s-1", link = "https://example.com/s-1"),
        )
        val event = MockEventType.serializedMockEvent("Some text").copy(
            signature = Signature.of("claude[agent]").withActor(actor),
        )
        val parsed = event.asJson().parse<SerializableEvent>()
        assertEquals(actor, parsed.signature?.actor)
        assertEquals(event, parsed)
    }

    @Test
    fun `A person's event has no actor after its serialization`() {
        val event = MockEventType.serializedMockEvent("Some text").copy(
            signature = Signature.of("alice"),
        )
        val parsed = event.asJson().parse<SerializableEvent>()
        assertEquals(null, parsed.signature?.actor)
    }

}
