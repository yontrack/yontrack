package net.nemerosa.ontrack.extension.indicators.values

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.IntNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.extension.indicators.IndicatorsTestFixtures
import net.nemerosa.ontrack.extension.indicators.support.PercentageThreshold
import net.nemerosa.ontrack.extension.indicators.support.percent
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PercentageIndicatorValueTypeTest {

    private val type = PercentageIndicatorValueType(IndicatorsTestFixtures.indicatorsExtensionFeature())

    @Test
    fun status() {
        assertEquals(0, type.status(threshold(50), 0.percent()).value)
        assertEquals(50, type.status(threshold(50), 25.percent()).value)
        assertEquals(100, type.status(threshold(50), 50.percent()).value)
        assertEquals(100, type.status(threshold(50), 75.percent()).value)
        assertEquals(100, type.status(threshold(50), 100.percent()).value)

        assertEquals(100, type.status(threshold(50, false), 0.percent()).value)
        assertEquals(100, type.status(threshold(50, false), 25.percent()).value)
        assertEquals(100, type.status(threshold(50, false), 50.percent()).value)
        assertEquals(50, type.status(threshold(50, false), 75.percent()).value)
        assertEquals(0, type.status(threshold(50, false), 100.percent()).value)
    }

    @Test
    fun toClientJson() {

        fun assertClientJson(threshold: PercentageThreshold, value: Int, expectedValue: Int) {
            val json = type.toClientJson(
                    threshold,
                    value.percent()
            )
            val expectedJson = mapOf(
                    "value" to expectedValue
            ).asJson()
            assertEquals(expectedJson, json)
        }

        assertClientJson(threshold = threshold(50, true), value = 25, expectedValue = 25)
        assertClientJson(threshold = threshold(50, true), value = 75, expectedValue = 75)
        assertClientJson(threshold = threshold(50, false), value = 25, expectedValue = 25)
        assertClientJson(threshold = threshold(50, false), value = 75, expectedValue = 75)

    }

    @Test
    fun fromClientJson() {

        fun assertClientJson(input: JsonNode, expectedValue: Int?) {
            val value = type.fromClientJson(threshold(50), input)
            assertEquals(expectedValue?.percent(), value)
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
            val value = type.fromStoredJson(threshold(50), input)
            assertEquals(expectedValue?.percent(), value)
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

        fun assertStoredJson(input: Int) {
            val stored = type.toStoredJson(threshold(50), input.percent())
            assertEquals(IntNode(input), stored)
        }

        assertStoredJson(25)
        assertStoredJson(75)

    }

    @Test
    fun toConfigForm() {

        fun assertConfigForm(threshold: PercentageThreshold) {
            assertEquals(
                    mapOf(
                            "threshold" to threshold.threshold,
                            "higherIsBetter" to threshold.higherIsBetter
                    ).asJson(),
                    type.toConfigForm(threshold)
            )
        }

        assertConfigForm(threshold(25, true))
        assertConfigForm(threshold(75, true))
        assertConfigForm(threshold(25, false))
        assertConfigForm(threshold(75, false))

    }

    @Test
    fun fromConfigForm() {

        fun assertConfigForm(input: JsonNode, expected: PercentageThreshold) {
            val config = type.fromConfigForm(input)
            assertEquals(expected.threshold, config.threshold)
            assertEquals(expected.higherIsBetter, config.higherIsBetter)
        }

        assertConfigForm(NullNode.instance, threshold(50, true))
        assertConfigForm("1".parseAsJson(), threshold(50, true))
        assertConfigForm(""""text"""".parseAsJson(), threshold(50, true))
        assertConfigForm("true".parseAsJson(), threshold(50, true))
        assertConfigForm("false".parseAsJson(), threshold(50, true))
        assertConfigForm(mapOf("threshold" to 25, "higherIsBetter" to true).asJson(), threshold(25, true))
        assertConfigForm(mapOf("threshold" to 75, "higherIsBetter" to true).asJson(), threshold(75, true))
        assertConfigForm(mapOf("threshold" to 25, "higherIsBetter" to false).asJson(), threshold(25, false))
        assertConfigForm(mapOf("threshold" to 75, "higherIsBetter" to false).asJson(), threshold(75, false))

    }

    @Test
    fun toConfigClientJson() {

        fun assertConfigClientJson(threshold: PercentageThreshold) {
            val config = type.toConfigClientJson(threshold)
            assertEquals(
                    mapOf(
                            "threshold" to threshold.threshold,
                            "higherIsBetter" to threshold.higherIsBetter
                    ).asJson(),
                    config
            )
        }

        assertConfigClientJson(threshold(25, true))
        assertConfigClientJson(threshold(75, true))
        assertConfigClientJson(threshold(25, false))
        assertConfigClientJson(threshold(75, false))

    }

    @Test
    fun toConfigStoredJson() {

        fun assertConfigStoredJson(threshold: PercentageThreshold) {
            val json = type.toConfigStoredJson(threshold)
            assertEquals(
                    mapOf(
                            "threshold" to threshold.threshold,
                            "higherIsBetter" to threshold.higherIsBetter
                    ).asJson(),
                    json
            )
        }

        assertConfigStoredJson(threshold(25, true))
        assertConfigStoredJson(threshold(75, true))
        assertConfigStoredJson(threshold(25, false))
        assertConfigStoredJson(threshold(75, false))

    }

    @Test
    fun fromConfigStoredJson() {

        fun assertFromStore(input: JsonNode, expected: PercentageThreshold) {
            val config = type.fromConfigStoredJson(input)
            assertEquals(expected, config)
        }

        assertFromStore(NullNode.instance, threshold(50, true))
        assertFromStore("1".parseAsJson(), threshold(50, true))
        assertFromStore(""""text"""".parseAsJson(), threshold(50, true))
        assertFromStore("true".parseAsJson(), threshold(50, true))
        assertFromStore("false".parseAsJson(), threshold(50, true))
        assertFromStore(mapOf("threshold" to 25, "higherIsBetter" to true).asJson(), threshold(25, true))
        assertFromStore(mapOf("threshold" to 75, "higherIsBetter" to true).asJson(), threshold(75, true))
        assertFromStore(mapOf("threshold" to 25, "higherIsBetter" to false).asJson(), threshold(25, false))
        assertFromStore(mapOf("threshold" to 75, "higherIsBetter" to false).asJson(), threshold(75, false))

    }

    @Test
    fun `Parsing of client config form`() {
        val config = type.fromConfigForm(
                mapOf(
                        "threshold" to 80,
                        "higherIsBetter" to true
                ).asJson()
        )
        assertEquals(80, config.threshold.value)
        assertEquals(true, config.higherIsBetter)
    }

    private fun threshold(value: Int, higherIsBetter: Boolean = true) = PercentageThreshold(
            threshold = value.percent(),
            higherIsBetter = higherIsBetter
    )

}