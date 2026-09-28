package net.nemerosa.ontrack.extension.scorecard.export

import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.metrics.Metric
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class ReadingMetricsTest {

    private val computedAt = LocalDateTime.of(2026, 9, 28, 2, 0, 12)

    private fun reading(value: Double?) = Reading(
        estateId = 12,
        projectId = 1,
        key = ReadingKeys.DELIVERY_LEAD_TIME,
        day = computedAt.toLocalDate(),
        computedAt = computedAt,
        windowStart = computedAt.minusDays(90),
        windowEnd = computedAt,
        value = value,
        basis = if (value == null) ReadingBasis.UNKNOWN else ReadingBasis.MEASURED,
        unknownReason = if (value == null) ReadingUnknownReason.NO_MARKER else null,
        details = mapOf("count" to 3).asJson(),
    )

    @Test
    fun `A measured reading is exported with its value, at the time it was computed`() {
        assertEquals(
            Metric(
                metric = "ontrack_reading",
                tags = mapOf(
                    "estate" to "Products",
                    "project" to "P",
                    "reading" to "delivery.leadTime",
                    "basis" to "MEASURED",
                ),
                fields = mapOf("value" to 3600.0),
                timestamp = computedAt,
            ),
            ReadingMetrics.metric(estate = "Products", project = "P", reading = reading(3600.0))
        )
    }

    @Test
    fun `An unknown reading is exported with no value`() {
        assertEquals(
            Metric(
                metric = "ontrack_reading",
                tags = mapOf(
                    "estate" to "-",
                    "project" to "P",
                    "reading" to "delivery.leadTime",
                    "basis" to "UNKNOWN",
                ),
                fields = emptyMap<String, Any>(),
                timestamp = computedAt,
            ),
            ReadingMetrics.metric(estate = "-", project = "P", reading = reading(null))
        )
    }
}
