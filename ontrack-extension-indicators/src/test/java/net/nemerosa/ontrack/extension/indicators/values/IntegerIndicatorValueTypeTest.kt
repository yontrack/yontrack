package net.nemerosa.ontrack.extension.indicators.values

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.IntNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.extension.indicators.IndicatorsTestFixtures
import net.nemerosa.ontrack.extension.indicators.support.IntegerThresholds
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IntegerIndicatorValueTypeTest {

    private val type = IntegerIndicatorValueType(IndicatorsTestFixtures.indicatorsExtensionFeature())

    @Test
    fun status() {
        assertEquals(100, type.status(threshold(), 0).value)
        assertEquals(50, type.status(threshold(), 5).value)
        assertEquals(0, type.status(threshold(), 10).value)
        assertEquals(0, type.status(threshold(), 15).value)
        assertEquals(0, type.status(threshold(higherIsBetter = true), 0).value)
        assertEquals(50, type.status(threshold(higherIsBetter = true), 5).value)
        assertEquals(100, type.status(threshold(higherIsBetter = true), 10).value)
        assertEquals(100, type.status(threshold(higherIsBetter = true), 15).value)
    }

    @Test
    fun toClientJson() {

        fun assertClientJson(threshold: IntegerThresholds, value: Int, expectedValue: Int) {
            val json = type.toClientJson(
                    threshold,
                    value
            )
            val expectedJson = mapOf(
                    "value" to expectedValue
            ).asJson()
            assertEquals(expectedJson, json)
        }

        assertClientJson(threshold = threshold(higherIsBetter = true), value = 25, expectedValue = 25)
        assertClientJson(threshold = threshold(higherIsBetter = true), value = 75, expectedValue = 75)
        assertClientJson(threshold = threshold(higherIsBetter = false), value = 25, expectedValue = 25)
        assertClientJson(threshold = threshold(higherIsBetter = false), value = 75, expectedValue = 75)

    }

    @Test
    fun fromClientJson() {

        fun assertClientJson(input: JsonNode, expectedValue: Int?) {
            val value = type.fromClientJson(threshold(), input)
            assertEquals(expectedValue, value)
        }

        assertClientJson(NullNode.instance, null)
        assertClientJson("true".parseAsJson(), null)
        assertClientJson("1".parseAsJson(), null)
        assertClientJson(""""text"""".parseAsJson(), null)
        assertClientJson(mapOf("test" to true).asJson(), null)

        assertClientJson(mapOf("value" to null).asJson(), null)
        assertClientJson(mapOf("value" to "text").asJson(), null)
        assertClientJson(mapOf("value" to 25).asJson(), 25)
        assertClientJson(mapOf("value" to 75).asJson(), 75)

    }

    @Test
    fun fromStoredJson() {

        fun assertStoredJson(input: JsonNode, expectedValue: Int?) {
            val value = type.fromStoredJson(threshold(), input)
            assertEquals(expectedValue, value)
        }

        assertStoredJson(NullNode.instance, null)
        assertStoredJson(""""text"""".parseAsJson(), null)
        assertStoredJson(mapOf("test" to true).asJson(), null)
        assertStoredJson("true".parseAsJson(), null)
        assertStoredJson("false".parseAsJson(), null)

        assertStoredJson("25".parseAsJson(), 25)
        assertStoredJson("75".parseAsJson(), 75)

    }

    @Test
    fun toStoredJson() {

        fun assertStoredJson(value: Int) {
            val stored = type.toStoredJson(threshold(), value)
            assertEquals(IntNode(value), stored)
        }

        assertStoredJson(25)
        assertStoredJson(75)

    }

    @Test
    fun toConfigForm() {

        fun assertConfigForm(threshold: IntegerThresholds) {
            assertEquals(
                    mapOf(
                            "min" to threshold.min,
                            "max" to threshold.max,
                            "higherIsBetter" to threshold.higherIsBetter
                    ).asJson(),
                    type.toConfigForm(threshold)
            )
        }

        assertConfigForm(threshold(0, 10, true))
        assertConfigForm(threshold(0, 10, false))

    }

    @Test
    fun fromConfigForm() {

        fun assertConfigForm(input: JsonNode, expected: IntegerThresholds) {
            val config = type.fromConfigForm(input)
            assertEquals(expected.min, config.min)
            assertEquals(expected.max, config.max)
            assertEquals(expected.higherIsBetter, config.higherIsBetter)
        }

        assertConfigForm(NullNode.instance, threshold())
        assertConfigForm("1".parseAsJson(), threshold())
        assertConfigForm(""""text"""".parseAsJson(), threshold())
        assertConfigForm("true".parseAsJson(), threshold())
        assertConfigForm("false".parseAsJson(), threshold())
        assertConfigForm(mapOf("min" to 0, "max" to 10, "higherIsBetter" to true).asJson(), threshold(higherIsBetter = true))
        assertConfigForm(mapOf("min" to 0, "max" to 10, "higherIsBetter" to false).asJson(), threshold(higherIsBetter = false))

    }

    @Test
    fun toConfigClientJson() {

        fun assertConfigClientJson(threshold: IntegerThresholds) {
            val config = type.toConfigClientJson(threshold)
            assertEquals(
                    mapOf(
                            "min" to threshold.min,
                            "max" to threshold.max,
                            "higherIsBetter" to threshold.higherIsBetter
                    ).asJson(),
                    config
            )
        }

        assertConfigClientJson(threshold(higherIsBetter = true))
        assertConfigClientJson(threshold(higherIsBetter = false))

    }

    @Test
    fun toConfigStoredJson() {

        fun assertConfigStoredJson(threshold: IntegerThresholds) {
            val json = type.toConfigStoredJson(threshold)
            assertEquals(
                    mapOf(
                            "min" to threshold.min,
                            "max" to threshold.max,
                            "higherIsBetter" to threshold.higherIsBetter
                    ).asJson(),
                    json
            )
        }

        assertConfigStoredJson(threshold(higherIsBetter = true))
        assertConfigStoredJson(threshold(higherIsBetter = false))

    }

    @Test
    fun fromConfigStoredJson() {

        fun assertFromStore(input: JsonNode, expected: IntegerThresholds) {
            val config = type.fromConfigStoredJson(input)
            assertEquals(expected, config)
        }

        assertFromStore(NullNode.instance, threshold())
        assertFromStore("1".parseAsJson(), threshold())
        assertFromStore(""""text"""".parseAsJson(), threshold())
        assertFromStore("true".parseAsJson(), threshold())
        assertFromStore("false".parseAsJson(), threshold())
        assertFromStore(mapOf("min" to 0, "max" to 10, "higherIsBetter" to true).asJson(), threshold(0, 10, true))
        assertFromStore(mapOf("min" to 0, "max" to 10, "higherIsBetter" to false).asJson(), threshold(0, 10, false))

    }

    private fun threshold(min: Int = 0, max: Int = 10, higherIsBetter: Boolean = false) = IntegerThresholds(
            min = min,
            max = max,
            higherIsBetter = higherIsBetter
    )

}