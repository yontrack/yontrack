package net.nemerosa.ontrack.model.json.schema

import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JsonNullableTypeTest {

    @Test
    fun `Json output of a nullable type`() {
        assertEquals(
            mapOf(
                "anyOf" to listOf(
                    mapOf("type" to "boolean", "description" to "A flag"),
                    mapOf("type" to "null", "description" to "No flag"),
                ),
            ).asJson(),
            JsonNullableType(JsonBooleanType("A flag"), nullDescription = "No flag").asJson(),
        )
    }

}
