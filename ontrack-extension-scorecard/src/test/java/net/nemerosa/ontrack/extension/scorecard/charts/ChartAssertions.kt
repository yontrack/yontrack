package net.nemerosa.ontrack.extension.scorecard.charts

import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Checks the values of a chart series, `NaN` for a period without any sample.
 */
fun assertValues(expected: List<Double>, actual: JsonNode, name: String = "data") {
    assertTrue(actual.isArray, "$name is an array")
    assertEquals(expected.size, actual.size(), "Number of values in $name")
    expected.forEachIndexed { index, value ->
        assertValue(value, actual.path(index), "$name[$index]")
    }
}

/**
 * Checks one value of a chart against a reading, `NaN` when there is none.
 */
fun assertValue(expected: Double, actual: JsonNode, name: String) {
    val value = actual.asDouble()
    if (expected.isNaN()) {
        assertTrue(value.isNaN(), "$name is NaN, got $actual")
    } else if (value.isNaN()) {
        fail("$name is $expected, got NaN")
    } else {
        assertEquals(expected, value, 1e-6, name)
    }
}
