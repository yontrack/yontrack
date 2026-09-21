package net.nemerosa.ontrack.json

import tools.jackson.databind.node.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class KTJsonUtilsTest {

    @Test
    fun `getTextField with existing value`() {
        assertEquals("12", mapOf("value" to "12").asJson().getTextField("value"))
    }

    @Test
    fun `getTextField with null value`() {
        assertEquals(null, mapOf("value" to null).asJson().getTextField("value"))
    }

    @Test
    fun `getTextField with no value`() {
        assertEquals(null, mapOf("other" to "12").asJson().getTextField("value"))
    }

    @Test
    fun `toObject with primitive types`() {
        assertEquals(null, null.toObject())
        assertEquals(null, NullNode.instance.toObject())
        assertEquals(true, BooleanNode.TRUE.toObject())
        assertEquals(10, IntNode(10).toObject())
        assertEquals(10L, LongNode(10L).toObject())
        assertEquals("Test", StringNode("Test").toObject())
    }

    @Test
    fun `toObject with array`() {
        assertEquals(
            listOf(
                true,
                10,
                10L,
                "Test"
            ),
            listOf(
                true,
                10,
                10L,
                "Test"
            ).asJson().toObject()
        )
    }

    @Test
    fun `toObject with object`() {
        assertEquals(
            mapOf(
                "id" to 10,
                "name" to "Test",
            ),
            Project(
                id = 10,
                name = "Test",
            ).asJson().toObject()
        )
    }

    @Test
    fun `toObject with several levels`() {
        assertEquals(
            mapOf(
                "id" to 10,
                "name" to "main",
                "builds" to listOf(
                    mapOf(
                        "id" to 100,
                        "name" to "1.0.0",
                    ),
                    mapOf(
                        "id" to 110,
                        "name" to "1.1.0",
                    ),
                )
            ),
            Branch(
                id = 10,
                name = "main",
                builds = listOf(
                    Build(id = 100, name = "1.0.0"),
                    Build(id = 110, name = "1.1.0"),
                )
            ).asJson().toObject()
        )
    }

    data class Project(
        val id: Int,
        val name: String,
    )

    data class Branch(
        val id: Int,
        val name: String,
        val builds: List<Build>,
    )

    data class Build(
        val id: Int,
        val name: String,
    )

}