package net.nemerosa.ontrack.extension.scorecard.charts

import net.nemerosa.ontrack.extension.chart.ChartService
import net.nemerosa.ontrack.extension.chart.GetChartInput
import net.nemerosa.ontrack.extension.chart.GetChartOptions
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.PromotionLevel
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.time.Month
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals

/**
 * The charts of a promotion level, read over four weeks, week by week.
 *
 * The reference time is fixed, so that no build or promotion falls on the edge of a week by chance.
 */
class PromotionLevelChartsIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var chartService: ChartService

    private val now = LocalDateTime.of(2026, Month.MARCH, 2, 12, 0)
    private val ref = now.minusDays(28)

    /**
     * Ends of the four weeks of the chart
     */
    private val weeks = (3L downTo 0L).map { now.minusWeeks(it).format(DateTimeFormatter.ISO_DATE) }

    /**
     * Promotions, as the number of hours after the creation of the build of the day
     */
    private val promotionHours: Map<Long, Long> = mapOf(
        // 1st week
        0L to 8,
        1L to 7,
        2L to 6,
        3L to 5,
        5L to 4,
        // 2nd week
        8L to 3,
        13L to 2,
        // 4th week
        21L to 1,
    )

    @Test
    fun `Promotion level lead time`() {
        withPromotionHours { pl ->
            val chart = chart("promotion-level-lead-time", mapOf("id" to pl.id()))
            assertDurationChart(
                chart,
                mean = listOf(21600.0, 9000.0, Double.NaN, 3600.0),
                percentile90 = listOf(28800.0, 10800.0, Double.NaN, 3600.0),
                maximum = listOf(28800.0, 10800.0, Double.NaN, 3600.0),
            )
        }
    }

    @Test
    fun `Promotion level lead time is bucketed by the first promotion of the build`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    // Created before the interval, promoted in the 1st week: 25 hours
                    build {
                        updateBuildSignature(time = ref.minusDays(1))
                        promote(pl, time = ref.plusHours(1))
                    }
                    // Created in the 1st week, promoted in the 2nd one: 2 days
                    build {
                        updateBuildSignature(time = ref.plusDays(6))
                        promote(pl, time = ref.plusDays(8))
                        // Promoted again in the 3rd week: not counted again
                        promote(pl, time = ref.plusDays(15))
                    }
                    val chart = chart("promotion-level-lead-time", mapOf("id" to pl.id()))
                    assertDurationChart(
                        chart,
                        mean = listOf(90000.0, 172800.0, Double.NaN, Double.NaN),
                        percentile90 = listOf(90000.0, 172800.0, Double.NaN, Double.NaN),
                        maximum = listOf(90000.0, 172800.0, Double.NaN, Double.NaN),
                    )
                }
            }
        }
    }

    @Test
    fun `E2E lead time on same project`() {
        withPromotionHours { pl ->
            val chart = chart(
                "e2e-lead-time",
                mapOf(
                    "refPromotionId" to pl.id(),
                    "samePromotion" to true,
                    "targetPromotionId" to null,
                    "targetProject" to pl.project.name,
                    "maxDepth" to 5,
                )
            )
            assertDurationChart(
                chart,
                mean = listOf(21600.0, 9000.0, Double.NaN, 3600.0),
                percentile90 = listOf(28800.0, 10800.0, Double.NaN, 3600.0),
                maximum = listOf(28800.0, 10800.0, Double.NaN, 3600.0),
            )
        }
    }

    @Test
    fun `Promotion level time to restore, from the first unpromoted build`() {
        withPromotionHours { pl ->
            val chart = chart("promotion-level-ttr", mapOf("id" to pl.id()))
            // Outages, bucketed by their restoration:
            // - day 4 to the promotion of day 5: 1 day and 4 hours
            // - day 6 to the promotion of day 8: 2 days and 3 hours
            // - day 9 to the promotion of day 13: 4 days and 2 hours
            // - day 14 to the promotion of day 21: 7 days and 1 hour
            // - from day 22, still going on
            assertDurationChart(
                chart,
                mean = listOf(100800.0, 268200.0, Double.NaN, 608400.0),
                percentile90 = listOf(100800.0, 352800.0, Double.NaN, 608400.0),
                maximum = listOf(100800.0, 352800.0, Double.NaN, 608400.0),
            )
        }
    }

    @Test
    fun `Promotion level frequency`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    val promotions = setOf(
                        0L, 1, 2, 3, 5, // 1st week
                        8, 13, // 2nd week
                        21, // 4th week
                    )
                    (0L..28).forEach { day ->
                        val time = ref.plusDays(day)
                        build(name = day.toString()) {
                            updateBuildSignature(time = time)
                            if (day in promotions) {
                                promote(pl, time = time)
                            }
                        }
                    }

                    val chart = chart("promotion-level-frequency", mapOf("id" to pl.id()))
                    assertEquals(weeks.asJson(), chart.path("dates"))
                    assertValues(listOf(5.0, 2.0, 0.0, 1.0), chart.path("data"))
                }
            }
        }
    }

    @Test
    fun `Promotion level success rate, builds in flight left out`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    // Promoted a day after their creation: the median lead time is one day
                    val promotions = setOf(
                        0L, 1, 2, 3, // 1st week, 4 out of 7
                        7, 8, // 2nd week, 2 out of 7
                        14, // 3rd week, 1 out of 7
                        21, // 4th week, 1 out of 6 - the build of the last day is in flight
                    )
                    (0L..27).forEach { day ->
                        val time = ref.plusDays(day)
                        build(name = day.toString()) {
                            updateBuildSignature(time = time)
                            if (day in promotions) {
                                promote(pl, time = time.plusDays(1))
                            }
                        }
                    }

                    val chart = chart("promotion-level-success-rate", mapOf("id" to pl.id()))
                    assertEquals(weeks.asJson(), chart.path("dates"))
                    assertValues(
                        listOf(
                            100.0 * 4 / 7,
                            100.0 * 2 / 7,
                            100.0 * 1 / 7,
                            100.0 * 1 / 6,
                        ),
                        chart.path("data")
                    )
                }
            }
        }
    }

    private fun withPromotionHours(code: (pl: PromotionLevel) -> Unit) {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    (0L..28).forEach { day ->
                        val time = ref.plusDays(day)
                        build(name = day.toString()) {
                            updateBuildSignature(time = time)
                            promotionHours[day]?.let { hours ->
                                promote(pl, time = time.plusHours(hours))
                            }
                        }
                    }
                    code(pl)
                }
            }
        }
    }

    private fun chart(name: String, parameters: Map<String, Any?>): JsonNode =
        chartService.getChart(
            GetChartInput(
                name = name,
                options = GetChartOptions(
                    ref = now,
                    interval = "4w",
                    period = "1w",
                ),
                parameters = parameters.asJson(),
            )
        )

    private fun assertDurationChart(
        chart: JsonNode,
        mean: List<Double>,
        percentile90: List<Double>,
        maximum: List<Double>,
    ) {
        assertEquals(
            listOf("Mean", "90th percentile", "Maximum").asJson(),
            chart.path("categories")
        )
        assertEquals(weeks.asJson(), chart.path("dates"))
        assertValues(mean, chart.path("data").path("mean"), "mean")
        assertValues(percentile90, chart.path("data").path("percentile90"), "percentile90")
        assertValues(maximum, chart.path("data").path("maximum"), "maximum")
    }

}
