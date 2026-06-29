package net.nemerosa.ontrack.kdsl.acceptance.tests.core

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.spec.PromotionLevel
import net.nemerosa.ontrack.kdsl.spec.charts.getChart
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.Month
import kotlin.test.assertEquals

class ACCE2EPromotions : AbstractACCDSLTestSupport() {

    @Test
    fun `End to end promotion data`() {
        end2endPromotionData { now, refPromotion, targetPromotion ->

            val chart = ontrack.getChart(
                name = "e2e-lead-time",
                ref = now,
                interval = "2m",
                period = "1w",
                parameters = mapOf(
                    "refPromotionId" to refPromotion.id,
                    "samePromotion" to false,
                    "targetPromotionId" to targetPromotion.id,
                    "targetProject" to targetPromotion.branch.project.name,
                    "maxDepth" to 5,
                ).asJson()
            )

            val expectedChartJson = mapOf(
                "categories" to listOf("Mean", "90th percentile", "Maximum"),
                "dates" to listOf(
                    "2024-02-02",
                    "2024-02-09",
                    "2024-02-16",
                    "2024-02-23",
                    "2024-03-01",
                    "2024-03-08",
                    "2024-03-15",
                    "2024-03-22",
                    "2024-03-29",
                ),
                "data" to mapOf(
                    "mean" to listOf("NaN", "NaN", 432000, "NaN", 108000, "NaN", "NaN", "NaN", "NaN"),
                    "percentile90" to listOf("NaN", "NaN", 432000, "NaN", 108000, "NaN", "NaN", "NaN", "NaN"),
                    "maximum" to listOf("NaN", "NaN", 432000, "NaN", 108000, "NaN", "NaN", "NaN", "NaN"),
                )
            ).asJson()

            assertEquals(
                expectedChartJson,
                chart
            )

        }
    }

    private fun end2endPromotionData(
        code: (now: LocalDateTime, refPromotion: PromotionLevel, targetPromotion: PromotionLevel) -> Unit
    ) {

        // Reference time
        val now = LocalDateTime.of(2024, Month.MARCH, 26, 17, 55)
        val ref = now.minusDays(40)

        lateinit var refPromotion: PromotionLevel
        lateinit var targetPromotion: PromotionLevel

        // Module
        project("module", deleteFirst = true) {
            branch("main") {
                refPromotion = promotion(ACCBuildLinks.GOLD)
                build("1") {
                    updateCreationTime(ref)
                    promote(ACCBuildLinks.GOLD, dateTime = ref)
                }
                build("2") {
                    updateCreationTime(ref.plusHours(180))
                }
                build("3") {
                    updateCreationTime(ref.plusHours(240))
                }
                build("4") {
                    updateCreationTime(ref.plusHours(300))
                    promote(ACCBuildLinks.GOLD, dateTime = ref.plusHours(300))
                }
            }
        }

        // Application
        project("application", deleteFirst = true) {
            branch("main") {
                promotion(ACCBuildLinks.GOLD)
                build("1") {
                    updateCreationTime(ref.plusHours(60))
                    linksTo("module" to "1")
                    promote(ACCBuildLinks.GOLD, dateTime = ref.plusHours(60))
                }
                build("2") {
                    updateCreationTime(ref.plusHours(210))
                    linksTo("module" to "2")
                }
                build("3") {
                    updateCreationTime(ref.plusHours(270))
                    linksTo("module" to "2")
                }
                build("4") {
                    updateCreationTime(ref.plusHours(315))
                    promote(ACCBuildLinks.GOLD, dateTime = ref.plusHours(315))
                    linksTo("module" to "4")
                }
            }
        }

        // Aggregator
        project("aggregator", deleteFirst = true) {
            branch("main") {
                targetPromotion = promotion(ACCBuildLinks.SILVER)
                build("1") {
                    updateCreationTime(ref.plusHours(120))
                    linksTo("application" to "1")
                    promote(ACCBuildLinks.SILVER, dateTime = ref.plusHours(120))
                }
                build("2") {
                    updateCreationTime(ref.plusHours(270))
                    linksTo("application" to "2")
                }
                build("3") {
                    updateCreationTime(ref.plusHours(300))
                    linksTo("application" to "2")
                }
                build("4") {
                    updateCreationTime(ref.plusHours(330))
                    promote(ACCBuildLinks.SILVER, dateTime = ref.plusHours(330))
                    linksTo("application" to "4")
                }
            }
        }

        // OK
        code(
            now,
            refPromotion,
            targetPromotion,
        )
    }

}