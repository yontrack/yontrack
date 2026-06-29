package net.nemerosa.ontrack.extension.dm.charts

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.chart.ChartService
import net.nemerosa.ontrack.extension.chart.GetChartInput
import net.nemerosa.ontrack.extension.chart.GetChartOptions
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.Signature
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals

internal class PromotionLevelChartsIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var chartService: ChartService

    @Test
    @Disabled("flaky")
    fun `Promotion level lead time`() {
        withLeadTime { now, pl ->
            val data = chartService.getChart(
                GetChartInput(
                    name = "promotion-level-lead-time",
                    options = GetChartOptions(
                        ref = now,
                        interval = "4w",
                        period = "1w"
                    ),
                    parameters = mapOf("id" to pl.id()).asJson()
                )
            )

            assertEquals(
                expectedLeadTimeData(now).asJson(),
                data
            )
        }
    }

    @Test
    @Disabled("flaky")
    fun `E2E lead time on same project`() {
        withLeadTime { now, pl ->
            val data = chartService.getChart(
                GetChartInput(
                    name = "e2e-lead-time",
                    options = GetChartOptions(
                        ref = now,
                        interval = "4w",
                        period = "1w"
                    ),
                    parameters = mapOf(
                        "refPromotionId" to pl.id(),
                        "targetProject" to pl.project.name,
                    ).asJson()
                )
            )

            assertEquals(
                expectedLeadTimeData(now).asJson(),
                data
            )
        }
    }

    private fun expectedLeadTimeData(now: LocalDateTime) = mapOf(
        "categories" to listOf(
            "Mean",
            "90th percentile",
            "Maximum",
        ),
        "dates" to (-4L..-1).map { n ->
            now.plusWeeks(n).format(DateTimeFormatter.ISO_DATE)
        },
        "data" to mapOf(
            "mean" to listOf(
                19800.0,
                9000.0,
                3600.0,
                Double.NaN,
            ),
            "percentile90" to listOf(
                25200.0,
                10800.0,
                3600.0,
                Double.NaN,
            ),
            "maximum" to listOf(
                25200.0,
                10800.0,
                3600.0,
                Double.NaN,
            ),
        )
    )

    private fun withLeadTime(
        code: (
            now: LocalDateTime,
            pl: PromotionLevel,
        ) -> Unit,
    ) {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    val now = Time.now()
                    val ref = now.minusDays(28)
                    val promotions: Map<Long, Long> = mapOf(
                        // 1st week
                        0L to 8,
                        1L to 7,
                        2L to 6,
                        3L to 5,
                        5L to 4,
                        // 2nd week
                        8L to 3,
                        13L to 2,
                        // 3rd week
                        21L to 1,
                    )
                    (0L..28).forEach { day ->
                        val time = ref.plusDays(day)
                        val build = build(name = day.toString()) {
                            updateBuildSignature(time = time)
                        }
                        val promotionTime = promotions[day]
                        if (promotionTime != null) {
                            build.promote(
                                pl, signature = Signature.of(
                                    time.plusHours(promotionTime),
                                    "test"
                                )
                            )
                        }
                    }

                    code(now, pl)
                }
            }
        }
    }

    @Test
    fun `Promotion level time to restore`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    val now = Time.now()
                    val ref = now.minusDays(28)
                    val promotions: Map<Long, Long> = mapOf(
                        // 1st week
                        0L to 8,
                        1L to 7,
                        2L to 6,
                        3L to 5,
                        5L to 4,
                        // 2nd week
                        8L to 3,
                        13L to 2,
                        // 3rd week
                        21L to 1,
                    )
                    (0L..28).forEach { day ->
                        val time = ref.plusDays(day)
                        val build = build(name = day.toString()) {
                            updateBuildSignature(time = time)
                        }
                        val promotionTime = promotions[day]
                        if (promotionTime != null) {
                            build.promote(
                                pl, signature = Signature.of(
                                    time.plusHours(promotionTime),
                                    "test"
                                )
                            )
                        }
                    }

                    val data = chartService.getChart(
                        GetChartInput(
                            name = "promotion-level-ttr",
                            options = GetChartOptions(
                                ref = now,
                                interval = "4w",
                                period = "1w"
                            ),
                            parameters = mapOf("id" to pl.id()).asJson()
                        )
                    )

                    assertEquals(
                        mapOf(
                            "categories" to listOf(
                                "Mean",
                                "90th percentile",
                                "Maximum",
                            ),
                            "dates" to (-4L..-1).map { n ->
                                now.plusWeeks(n + 1).format(DateTimeFormatter.ISO_DATE)
                            },
                            "data" to mapOf(
                                "mean" to listOf(
                                    100800.0,
                                    95400.0,
                                    Double.NaN,
                                    90000.0,
                                ),
                                "percentile90" to listOf(
                                    100800.0,
                                    97200.0,
                                    Double.NaN,
                                    90000.0,
                                ),
                                "maximum" to listOf(
                                    100800.0,
                                    97200.0,
                                    Double.NaN,
                                    90000.0,
                                ),
                            )
                        ).asJson(),
                        data
                    )
                }
            }
        }
    }

    @Test
    @Disabled("flaky")
    fun `Promotion level frequency`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    val now = Time.now()
                    val ref = now.minusDays(28)
                    val promotions = setOf(
                        0L, 1, 2, 3, 5, // 1st week
                        8, 13, // 2nd week
                        21, // 3rd week
                    )
                    (0L..28).forEach { day ->
                        val time = ref.plusDays(day)
                        val build = build(name = day.toString()) {
                            updateBuildSignature(time = time)
                        }
                        if (day in promotions) {
                            build.promote(
                                pl, signature = Signature.of(
                                    time,
                                    "test"
                                )
                            )
                        }
                    }

                    val data = chartService.getChart(
                        GetChartInput(
                            name = "promotion-level-frequency",
                            options = GetChartOptions(
                                ref = now,
                                interval = "4w",
                                period = "1w"
                            ),
                            parameters = mapOf("id" to pl.id()).asJson()
                        )
                    )

                    assertEquals(
                        mapOf(
                            "dates" to (-4L..-1).map { n ->
                                now.plusWeeks(n).format(DateTimeFormatter.ISO_DATE)
                            },
                            "data" to listOf(
                                4.0,
                                2.0,
                                1.0,
                                0.0
                            )
                        ).asJson(),
                        data
                    )
                }
            }
        }
    }

    @Test
    fun `Promotion level success rate`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    val now = Time.now()
                    val ref = now.minusDays(28)
                    val promotions = setOf(
                        1L, 2, 3, 5, // 1st week, 4 out of 7
                        8, 13, // 2nd week, 2 out of 7
                        21, // 3rd week, 1 out of 7
                        // 4th week, 0 out of 7
                    )
                    (1L..28).forEach { day ->
                        val time = ref.plusDays(day)
                        val build = build(name = day.toString()) {
                            updateBuildSignature(time = time)
                        }
                        if (day in promotions) {
                            build.promote(
                                pl, signature = Signature.of(
                                    time,
                                    "test"
                                )
                            )
                        }
                    }

                    val data = chartService.getChart(
                        GetChartInput(
                            name = "promotion-level-success-rate",
                            options = GetChartOptions(
                                ref = now,
                                interval = "4w",
                                period = "1w"
                            ),
                            parameters = mapOf("id" to pl.id()).asJson()
                        )
                    )

                    assertEquals(
                        mapOf(
                            "dates" to (-4L..-1).map { n ->
                                now.plusWeeks(n + 1).format(DateTimeFormatter.ISO_DATE)
                            },
                            "data" to listOf(
                                57.142857142857146,
                                28.571428571428573,
                                14.285714285714285,
                                0.0
                            )
                        ).asJson(),
                        data
                    )
                }
            }
        }
    }

}