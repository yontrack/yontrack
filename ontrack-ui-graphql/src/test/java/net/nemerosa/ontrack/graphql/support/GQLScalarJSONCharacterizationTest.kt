package net.nemerosa.ontrack.graphql.support

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * What the `JSON` scalar hands to the GraphQL output, pinned before the Jackson 3 migration
 * (#1843). Written on Jackson 2, and meant to pass unchanged on Jackson 3: it names no Jackson type.
 */
class GQLScalarJSONCharacterizationTest {

    data class Payload(
        val name: String,
        val time: LocalDateTime,
        val count: Int,
        val ratio: Double,
        val tags: List<String>,
        val password: String?,
        val nested: Nested,
    )

    data class Nested(
        val token: String,
        val privateKey: String?,
        val tokens: List<String>,
        val enabled: Boolean,
    )

    @Suppress("DEPRECATION")
    private fun serialize(value: Any): String =
        GQLScalarJSON.TYPE.coercing.serialize(value).toString()

    @Test
    fun `A bean is written with its dates and without its secrets`() {
        assertEquals(
            """{"name":"n","time":"2025-11-04T09:12:30.123400Z","count":1,"ratio":0.5,"tags":["a","b"],"nested":{"tokens":["t1"],"enabled":true}}""",
            serialize(
                Payload(
                    name = "n",
                    time = LocalDateTime.of(2025, 11, 4, 9, 12, 30, 123_400_000),
                    count = 1,
                    ratio = 0.5,
                    tags = listOf("a", "b"),
                    password = "secret",
                    nested = Nested(
                        token = "secret",
                        privateKey = null,
                        tokens = listOf("t1"),
                        enabled = true,
                    ),
                )
            )
        )
    }

    @Test
    fun `A map is written as an object`() {
        assertEquals(
            """{"a":1,"b":null,"c":[true,"x"]}""",
            serialize(linkedMapOf("a" to 1, "b" to null, "c" to listOf(true, "x")))
        )
    }

    @Test
    fun `A parsed string is written back as it was read`() {
        val coercing = GQLScalarJSON.TYPE.coercing
        @Suppress("DEPRECATION")
        val node = coercing.parseValue("""{"x":1.50,"y":"text","z":[1,2]}""")!!
        assertEquals("""{"x":1.5,"y":"text","z":[1,2]}""", serialize(node))
        @Suppress("DEPRECATION")
        assertEquals("\"not json\"", coercing.parseValue("not json").toString())
    }

}
