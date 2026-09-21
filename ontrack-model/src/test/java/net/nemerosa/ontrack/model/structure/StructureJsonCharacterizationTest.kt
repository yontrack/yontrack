package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.json.ObjectMapperFactory
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.asJsonString
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * Pins the JSON of the structure types as 5.x writes and stores it, so that the Jackson 2 → 3
 * migration (#1843) keeps it. Written on Jackson 2, and meant to pass unchanged on Jackson 3: it
 * names no Jackson type.
 */
class StructureJsonCharacterizationTest {

    private val signature = Signature(
        time = LocalDateTime.of(2025, 11, 4, 9, 12, 30, 123_400_000),
        user = User("admin"),
    )

    private val project = Project(
        id = ID.of(1),
        name = "P",
        description = "Project",
        isDisabled = false,
        signature = signature,
    )

    private val branch = Branch(
        id = ID.of(2),
        name = "main",
        description = null,
        isDisabled = false,
        project = project,
        signature = signature,
    )

    private val build = Build(
        id = ID.of(3),
        name = "1.0.0",
        description = "Build",
        signature = signature,
        branch = branch,
    )

    @Test
    fun `ID round-trips as a number`() {
        assertEquals("12", ObjectMapperFactory.create().writeValueAsString(ID.of(12)))
        assertEquals("0", ObjectMapperFactory.create().writeValueAsString(ID.NONE))
        assertEquals(ID.of(12), ObjectMapperFactory.create().readValue("12", ID::class.java))
        assertEquals(ID.NONE, ObjectMapperFactory.create().readValue("null", ID::class.java) ?: ID.NONE)
    }

    @Test
    fun `Signature round-trips`() {
        val json = """{"time":"2025-11-04T09:12:30.123400Z","user":{"name":"admin"}}"""
        assertEquals(json, signature.asJson().asJsonString())
        assertEquals(signature, json.parseAsJson().parse<Signature>())
    }

    @Test
    fun `Signature stored by 5_x`() {
        val stored = """{"time":"2021-06-01T10:20:30.123456789Z","user":{"name":"user"}}"""
        assertEquals(
            Signature(LocalDateTime.of(2021, 6, 1, 10, 20, 30, 123_456_789), User("user")),
            stored.parseAsJson().parse<Signature>(),
        )
    }

    @Test
    fun `Build is written with its branch and project`() {
        assertEquals(
            """{"id":3,"name":"1.0.0","description":"Build","signature":{"time":"2025-11-04T09:12:30.123400Z","user":{"name":"admin"}},"branch":{"id":2,"name":"main","description":null,"disabled":false,"project":{"id":1,"name":"P","description":"Project","disabled":false,"signature":{"time":"2025-11-04T09:12:30.123400Z","user":{"name":"admin"}}},"signature":{"time":"2025-11-04T09:12:30.123400Z","user":{"name":"admin"}}}}""",
            ObjectMapperFactory.create().writeValueAsString(build),
        )
    }

}
