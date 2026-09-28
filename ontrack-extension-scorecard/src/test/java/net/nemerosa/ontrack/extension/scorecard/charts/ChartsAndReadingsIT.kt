package net.nemerosa.ontrack.extension.scorecard.charts

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.chart.ChartService
import net.nemerosa.ontrack.extension.chart.GetChartInput
import net.nemerosa.ontrack.extension.chart.GetChartOptions
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingEngine
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.PromotionLevel
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.time.Duration
import kotlin.test.assertEquals

/**
 * A promotion-level chart read over the window of a reading, in one single period, gives the
 * numbers of the reading: they are computed from the same samples.
 */
class ChartsAndReadingsIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var chartService: ChartService

    @Autowired
    private lateinit var readingEngine: ReadingEngine

    private val now = Time.now.withNano(0)

    @Test
    fun `Charts and readings agree on the same interval`() {
        asAdmin {
            project {
                branch("main") {
                    val gold = promotionLevel("GOLD")
                    // Promoted in 1 day
                    build().apply {
                        updateBuildSignature(time = now.minusDays(80))
                        promote(gold, time = now.minusDays(79))
                    }
                    // Outage from this first unpromoted build...
                    build().updateBuildSignature(time = now.minusDays(70))
                    build().updateBuildSignature(time = now.minusDays(65))
                    // ... restored in 13 days by a promotion in 3 days
                    build().apply {
                        updateBuildSignature(time = now.minusDays(60))
                        promote(gold, time = now.minusDays(57))
                    }
                    // Promoted in 2 days
                    build().apply {
                        updateBuildSignature(time = now.minusDays(40))
                        promote(gold, time = now.minusDays(38))
                    }
                    // Promoted in 1 day, and once more later
                    build().apply {
                        updateBuildSignature(time = now.minusDays(30))
                        promote(gold, time = now.minusDays(29))
                        promote(gold, time = now.minusDays(20))
                    }
                    // Outage restored in 10 days, by a promotion in 5 days
                    build().updateBuildSignature(time = now.minusDays(20))
                    build().apply {
                        updateBuildSignature(time = now.minusDays(15))
                        promote(gold, time = now.minusDays(10))
                    }
                    // Outage still going on
                    build().updateBuildSignature(time = now.minusDays(5))
                    // In flight
                    build().updateBuildSignature(time = now.minusHours(12))

                    val readings = readingEngine.computeProject(NoEstateReadingSet, project)!!
                        .associateBy { it.key }

                    // Lead time
                    val leadTime = readings.getValue(ReadingKeys.DELIVERY_LEAD_TIME)
                    assertEquals(5, leadTime.details.path("count").asInt())
                    val leadTimeChart = chart("promotion-level-lead-time", gold, leadTime)
                    assertDurationDetails(leadTime, leadTimeChart)

                    // Frequency
                    val frequency = readings.getValue(ReadingKeys.DELIVERY_FREQUENCY)
                    assertEquals(6, frequency.details.path("count").asInt())
                    val frequencyChart = chart("promotion-level-frequency", gold, frequency)
                    assertValues(listOf(frequency.details.path("count").asDouble()), frequencyChart.path("data"))

                    // Success rate
                    val successRate = readings.getValue(ReadingKeys.DELIVERY_SUCCESS_RATE)
                    assertEquals(ReadingBasis.MEASURED, successRate.basis)
                    assertEquals(9, successRate.details.path("count").asInt())
                    assertEquals(1, successRate.details.path("inFlight").path("excluded").asInt())
                    val successRateChart = chart("promotion-level-success-rate", gold, successRate)
                    assertValues(listOf(successRate.value!!), successRateChart.path("data"))

                    // Time to restore
                    val mttr = readings.getValue(ReadingKeys.DELIVERY_MTTR)
                    assertEquals(2, mttr.details.path("count").asInt())
                    val ttrChart = chart("promotion-level-ttr", gold, mttr)
                    assertDurationDetails(mttr, ttrChart)
                }
            }
        }
    }

    /**
     * Gets a chart over the window of the reading, in one period.
     */
    private fun chart(name: String, level: PromotionLevel, reading: Reading): JsonNode {
        val days = Duration.between(reading.windowStart, reading.windowEnd).toDays()
        return chartService.getChart(
            GetChartInput(
                name = name,
                options = GetChartOptions(
                    ref = reading.windowEnd,
                    interval = "${days}d",
                    period = "${days}d",
                ),
                parameters = mapOf("id" to level.id()).asJson(),
            )
        )
    }

    private fun assertDurationDetails(reading: Reading, chart: JsonNode) {
        assertEquals(ReadingBasis.MEASURED, reading.basis)
        val data = chart.path("data")
        assertValue(reading.details.path("mean").asDouble(), data.path("mean").path(0), "mean")
        assertValue(reading.details.path("p90").asDouble(), data.path("percentile90").path(0), "p90")
        assertValue(reading.details.path("max").asDouble(), data.path("maximum").path(0), "max")
        assertEquals(1, data.path("mean").size(), "One single period")
    }
}
