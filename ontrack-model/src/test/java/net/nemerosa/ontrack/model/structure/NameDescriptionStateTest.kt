package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NameDescriptionStateTest {

    @Test
    fun `To JSON`() {
        assertEquals(
                mapOf(
                        "name" to "My name",
                        "description" to "My description",
                        "disabled" to true
                ).asJson(),
                NameDescriptionState("My name", "My description", true).asJson()
        )
    }

    @Test
    fun `From JSON with disabled set to true`() {
        val state = mapOf(
                "name" to "my-name",
                "description" to "Some description",
                "disabled" to true
        ).asJson().parse<NameDescriptionState>()
        assertEquals("my-name", state.name)
        assertEquals("Some description", state.description)
        assertEquals(true, state.isDisabled)
    }

    @Test
    fun `From JSON with disabled set to false`() {
        val state = mapOf(
                "name" to "my-name",
                "description" to "Some description",
                "disabled" to false
        ).asJson().parse<NameDescriptionState>()
        assertEquals("my-name", state.name)
        assertEquals("Some description", state.description)
        assertEquals(false, state.isDisabled)
    }

    @Test
    fun `From JSON with disabled set to null`() {
        val state = mapOf(
                "name" to "my-name",
                "description" to "Some description",
                "disabled" to null
        ).asJson().parse<NameDescriptionState>()
        assertEquals("my-name", state.name)
        assertEquals("Some description", state.description)
        assertEquals(false, state.isDisabled)
    }

}