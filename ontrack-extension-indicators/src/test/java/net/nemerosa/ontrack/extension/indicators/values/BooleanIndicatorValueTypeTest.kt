package net.nemerosa.ontrack.extension.indicators.values

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.BooleanNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.extension.indicators.IndicatorsTestFixtures
import net.nemerosa.ontrack.extension.indicators.model.IndicatorCompliance
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BooleanIndicatorValueTypeTest {

    private val type = BooleanIndicatorValueType(IndicatorsTestFixtures.indicatorsExtensionFeature())

    @Test
    fun status() {
        assertEquals(IndicatorCompliance.MEDIUM, type.status(BooleanIndicatorValueTypeConfig(false), false))
        assertEquals(IndicatorCompliance.HIGHEST, type.status(BooleanIndicatorValueTypeConfig(false), true))
        assertEquals(IndicatorCompliance.LOWEST, type.status(BooleanIndicatorValueTypeConfig(true), false))
        assertEquals(IndicatorCompliance.HIGHEST, type.status(BooleanIndicatorValueTypeConfig(true), true))
    }

    @Test
    fun toClientJson() {

        fun assertClientJson(required: Boolean, value: Boolean, expectedValue: String) {
            val json = type.toClientJson(
                    BooleanIndicatorValueTypeConfig(required),
                    value
            )
            val expectedJson = mapOf(
                    "value" to expectedValue
            ).asJson()
            assertEquals(expectedJson, json)
        }

        assertClientJson(required = false, value = false, expectedValue = "false")
        assertClientJson(required = false, value = true, expectedValue = "true")
        assertClientJson(required = true, value = false, expectedValue = "false")
        assertClientJson(required = true, value = true, expectedValue = "true")

    }

    @Test
    fun fromClientJson() {

        fun assertClientJson(input: JsonNode, expectedValue: Boolean?) {
            val value = type.fromClientJson(BooleanIndicatorValueTypeConfig(false), input)
            assertEquals(expectedValue, value)
        }

        assertClientJson(NullNode.instance, null)
        assertClientJson("true".parseAsJson(), null)
        assertClientJson("1".parseAsJson(), null)
        assertClientJson(""""text"""".parseAsJson(), null)
        assertClientJson(mapOf("test" to true).asJson(), null)

        assertClientJson(mapOf("value" to null).asJson(), null)
        assertClientJson(mapOf("value" to false).asJson(), false)
        assertClientJson(mapOf("value" to true).asJson(), true)

    }

    @Test
    fun fromStoredJson() {

        fun assertStoredJson(input: JsonNode, expectedValue: Boolean?) {
            val value = type.fromStoredJson(BooleanIndicatorValueTypeConfig(false), input)
            assertEquals(expectedValue, value)
        }

        assertStoredJson(NullNode.instance, null)
        assertStoredJson("1".parseAsJson(), null)
        assertStoredJson(""""text"""".parseAsJson(), null)
        assertStoredJson(mapOf("test" to true).asJson(), null)

        assertStoredJson("true".parseAsJson(), true)
        assertStoredJson("false".parseAsJson(), false)

    }

    @Test
    fun toStoredJson() {

        fun assertStoredJson(input: Boolean, expectedNode: BooleanNode) {
            val stored = type.toStoredJson(BooleanIndicatorValueTypeConfig(false), input)
            assertEquals(expectedNode, stored)
        }

        assertStoredJson(false, BooleanNode.FALSE)
        assertStoredJson(true, BooleanNode.TRUE)

    }

    @Test
    fun toConfigForm() {

        assertEquals(
                mapOf("required" to false).asJson(),
                type.toConfigForm(BooleanIndicatorValueTypeConfig(false))
        )

        assertEquals(
                mapOf("required" to true).asJson(),
                type.toConfigForm(BooleanIndicatorValueTypeConfig(true))
        )

    }

    @Test
    fun fromConfigForm() {

        fun assertConfigForm(input: JsonNode, expectedRequired: Boolean) {
            val config = type.fromConfigForm(input)
            assertEquals(expectedRequired, config.required)
        }

        assertConfigForm(NullNode.instance, expectedRequired = true)
        assertConfigForm("1".parseAsJson(), expectedRequired = true)
        assertConfigForm(""""text"""".parseAsJson(), expectedRequired = true)
        assertConfigForm("true".parseAsJson(), expectedRequired = true)
        assertConfigForm("false".parseAsJson(), expectedRequired = true)
        assertConfigForm(mapOf("required" to 1).asJson(), expectedRequired = true)
        assertConfigForm(mapOf("required" to true).asJson(), expectedRequired = true)
        assertConfigForm(mapOf("required" to false).asJson(), expectedRequired = false)

    }

    @Test
    fun toConfigClientJson() {

        assertEquals(
                mapOf("required" to false).asJson(),
                type.toConfigClientJson(BooleanIndicatorValueTypeConfig(false))
        )

        assertEquals(
                mapOf("required" to true).asJson(),
                type.toConfigClientJson(BooleanIndicatorValueTypeConfig(true))
        )

    }

    @Test
    fun toConfigStoredJson() {

        assertEquals(
                mapOf("required" to false).asJson(),
                type.toConfigStoredJson(BooleanIndicatorValueTypeConfig(false))
        )

        assertEquals(
                mapOf("required" to true).asJson(),
                type.toConfigStoredJson(BooleanIndicatorValueTypeConfig(true))
        )

    }

    @Test
    fun fromConfigStoredJson() {

        fun assertFromStore(input: JsonNode, expectedRequired: Boolean) {
            val config = type.fromConfigStoredJson(input)
            assertEquals(expectedRequired, config.required)
        }

        assertFromStore(NullNode.instance, expectedRequired = true)
        assertFromStore("1".parseAsJson(), expectedRequired = true)
        assertFromStore(""""text"""".parseAsJson(), expectedRequired = true)
        assertFromStore("true".parseAsJson(), expectedRequired = true)
        assertFromStore("false".parseAsJson(), expectedRequired = true)
        assertFromStore(mapOf("required" to 1).asJson(), expectedRequired = true)
        assertFromStore(mapOf("required" to true).asJson(), expectedRequired = true)
        assertFromStore(mapOf("required" to false).asJson(), expectedRequired = false)


    }

}