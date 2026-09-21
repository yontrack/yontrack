package net.nemerosa.ontrack.graphql.support

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import graphql.language.*
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GQLScalarJSONTest {

    @Test
    fun `Parse string value`() {
        val json = GQLScalarJSON.TYPE.coercing.parseValue("""{"passed":15}""")
        assertEquals(
            mapOf(
                "passed" to 15
            ).asJson(),
            json
        )
    }

    @Test
    fun `Parse JSON value`() {
        val json = GQLScalarJSON.TYPE.coercing.parseValue(
            mapOf(
                "passed" to 15
            ).asJson()
        )
        assertEquals(
            mapOf(
                "passed" to 15
            ).asJson(),
            json
        )
    }

    @Test
    fun `Parse map value`() {
        val json = GQLScalarJSON.TYPE.coercing.parseValue(
            mapOf(
                "passed" to 15
            )
        )
        assertEquals(
            mapOf(
                "passed" to 15
            ).asJson(),
            json
        )
    }

    @Test
    fun `Parse literal object`() {
        val input = ObjectValue(
            listOf(
                ObjectField("value", IntValue(30.toBigInteger()))
            )
        )
        val value = GQLScalarJSON.TYPE.coercing.parseLiteral(input)
        assertIs<ObjectNode>(value) {
            assertEquals(30, it.path("value").asInt())
        }
    }

    @Test
    fun `Parse literal array`() {
        val input = ArrayValue(
            listOf(
                StringValue("one"),
                StringValue("two")
            )
        )
        val value = GQLScalarJSON.TYPE.coercing.parseLiteral(input)
        assertIs<ArrayNode>(value) {
            assertEquals(
                listOf("one", "two"),
                it.values().map { it.asText() }
            )
        }
    }

    @Test
    fun `JSON text`() {
        // Data as string
        val data = "Some text"
        // Serialization
        val json = GQLScalarJSON.TYPE.coercing.serialize(data) as JsonNode
        // Checks the data
        assertEquals("Some text", json.asText())
    }

    @Test
    fun `Obfuscation of password`() {
        // Data with password
        val data = SampleConfig(user = "user", password = "secret")
        // As JSON scalar
        val json = GQLScalarJSON.TYPE.coercing.serialize(data) as JsonNode
        // Checks the data
        assertEquals("user", json["user"].asText())
        assertFalse(json.has("password"), "Password field removed")
    }

    @Test
    fun `Obfuscation of token`() {
        // Data with password
        val data = SampleConfig(token = "token")
        // As JSON scalar
        val json = GQLScalarJSON.TYPE.coercing.serialize(data) as JsonNode
        // Checks the data
        assertFalse(json.has("token"), "Token field removed")
    }

    @Test
    fun `Obfuscation of oauth2Token`() {
        // Data with password
        val data = SampleConfig(oauth2Token = "token")
        // As JSON scalar
        val json = GQLScalarJSON.TYPE.coercing.serialize(data) as JsonNode
        // Checks the data
        assertFalse(json.has("oauth2Token"), "oauth2Token field removed")
    }

    @Test
    fun `Obfuscation of appPrivateKey`() {
        // Data with password
        val data = SampleConfig(appPrivateKey = "some-key")
        // As JSON scalar
        val json = GQLScalarJSON.TYPE.coercing.serialize(data) as JsonNode
        // Checks the data
        assertFalse(json.has("appPrivateKey"), "appPrivateKey field removed")
    }

    @Test
    fun `Deep obfuscation of password in objects`() {
        // Data with password
        val data = SampleData("value", SampleConfig("user", "secret"))
        // As JSON scaler
        val json = GQLScalarJSON.TYPE.coercing.serialize(data) as JsonNode
        // Checks the data
        assertEquals("value", json["value"].asText())
        assertEquals("user", json["config"]["user"].asText())
        assertFalse(json.path("config").has("password"), "Password field removed")
    }

    @Test
    fun `Deep obfuscation of password in arrays`() {
        // Data with password
        val data = SampleArray(
            "value",
            listOf(
                SampleConfig("user", "secret")
            )
        )
        // As JSON scaler
        val json = GQLScalarJSON.TYPE.coercing.serialize(data) as JsonNode
        // Checks the data
        assertEquals("value", json["value"].asText())
        assertEquals("user", json["configs"][0]["user"].asText())
        assertFalse(json.path("configs").single().has("password"), "Password field removed")
    }

    data class SampleConfig(
        val user: String? = null,
        val password: String? = null,
        val token: String? = null,
        val oauth2Token: String? = null,
        val appPrivateKey: String? = null,
    )

    data class SampleData(
        val value: String,
        val config: SampleConfig
    )

    data class SampleArray(
        val value: String,
        val configs: List<SampleConfig>
    )

}