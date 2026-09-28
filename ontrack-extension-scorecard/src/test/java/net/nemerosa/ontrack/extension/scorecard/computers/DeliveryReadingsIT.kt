package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingEngine
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The delivery readings under the promotion marker of the no-estate set,
 * computed by the engine on backdated builds and promotions.
 */
class DeliveryReadingsIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var readingEngine: ReadingEngine

    private val now = Time.now.withNano(0)

    private fun Project.readings(): Map<String, Reading> =
        readingEngine.computeProject(NoEstateReadingSet, this)!!.associateBy { it.key }

    @Test
    fun `Lead time and frequency pooled across the branches, up to their last level`() {
        asAdmin {
            project {
                branch("main") {
                    val silver = promotionLevel("SILVER")
                    val gold = promotionLevel("GOLD")
                    // 1 day to GOLD
                    build().apply {
                        updateBuildSignature(time = now.minusDays(20))
                        promote(silver, time = now.minusDays(20).plusHours(1))
                        promote(gold, time = now.minusDays(19))
                    }
                    // 3 days to GOLD
                    build().apply {
                        updateBuildSignature(time = now.minusDays(10))
                        promote(gold, time = now.minusDays(7))
                    }
                    // SILVER only, not counted
                    build().apply {
                        updateBuildSignature(time = now.minusDays(5))
                        promote(silver, time = now.minusDays(4))
                    }
                }
                branch("release") {
                    val platinum = promotionLevel("PLATINUM")
                    // 2 days to PLATINUM
                    build().apply {
                        updateBuildSignature(time = now.minusDays(30))
                        promote(platinum, time = now.minusDays(28))
                    }
                    // Promoted before the window: not counted
                    build().apply {
                        updateBuildSignature(time = now.minusDays(200))
                        promote(platinum, time = now.minusDays(100))
                    }
                }

                val readings = readings()

                val leadTime = readings.getValue(ReadingKeys.DELIVERY_LEAD_TIME)
                assertEquals(ReadingBasis.MEASURED, leadTime.basis)
                assertEquals(2 * 86400.0, leadTime.value)
                assertEquals(3, leadTime.details.path("count").asInt())
                assertEquals(86400.0, leadTime.details.path("min").asDouble())
                assertEquals(3 * 86400.0, leadTime.details.path("max").asDouble())
                assertEquals(2 * 86400.0, leadTime.details.path("mean").asDouble())
                assertEquals("PROMOTION", leadTime.details.path("markerKind").asText())
                assertEquals("GOLD", leadTime.details.path("marker").path("levels").path("main").asText())
                assertEquals("PLATINUM", leadTime.details.path("marker").path("levels").path("release").asText())
                assertEquals("ALL_BRANCHES", leadTime.details.path("scope").path("kind").asText())
                assertEquals(90L, java.time.Duration.between(leadTime.windowStart, leadTime.windowEnd).toDays())

                val frequency = readings.getValue(ReadingKeys.DELIVERY_FREQUENCY)
                assertEquals(ReadingBasis.MEASURED, frequency.basis)
                assertEquals(3, frequency.details.path("count").asInt())
                assertEquals(3 * 7.0 / 90.0, frequency.value!!, 1e-9)
            }
        }
    }

    @Test
    fun `Success rate and time to restore pooled across the branches, up to their last level`() {
        asAdmin {
            project {
                branch("main") {
                    promotionLevel("SILVER")
                    val gold = promotionLevel("GOLD")
                    // Promoted in 1 day
                    build().apply {
                        updateBuildSignature(time = now.minusDays(30))
                        promote(gold, time = now.minusDays(29))
                    }
                    // Outage: from the first unpromoted build...
                    build().updateBuildSignature(time = now.minusDays(20))
                    build().updateBuildSignature(time = now.minusDays(15))
                    // ... to the next promotion, in 2 days
                    build().apply {
                        updateBuildSignature(time = now.minusDays(10))
                        promote(gold, time = now.minusDays(8))
                    }
                    // In flight (created within the median lead time of 2 days)
                    build().updateBuildSignature(time = now.minusDays(1))
                }
                branch("release") {
                    val platinum = promotionLevel("PLATINUM")
                    // Promoted in 3 days
                    build().apply {
                        updateBuildSignature(time = now.minusDays(25))
                        promote(platinum, time = now.minusDays(22))
                    }
                    // Not promoted, not in flight: an outage still going on
                    build().updateBuildSignature(time = now.minusDays(5))
                }

                val readings = readings()

                val successRate = readings.getValue(ReadingKeys.DELIVERY_SUCCESS_RATE)
                assertEquals(ReadingBasis.MEASURED, successRate.basis)
                assertEquals(50.0, successRate.value)
                assertEquals(6, successRate.details.path("count").asInt())
                assertEquals(3, successRate.details.path("promoted").asInt())
                assertEquals(2 * 86400.0, successRate.details.path("inFlight").path("leadTime").asDouble())
                assertEquals(1, successRate.details.path("inFlight").path("excluded").asInt())
                assertEquals("PROMOTION", successRate.details.path("markerKind").asText())
                assertEquals("GOLD", successRate.details.path("marker").path("levels").path("main").asText())

                val mttr = readings.getValue(ReadingKeys.DELIVERY_MTTR)
                assertEquals(ReadingBasis.MEASURED, mttr.basis)
                assertEquals(12 * 86400.0, mttr.value)
                assertEquals(1, mttr.details.path("count").asInt())
                assertEquals(1, mttr.details.path("open").asInt())
                assertEquals(1, mttr.details.path("inFlight").path("excluded").asInt())
                assertEquals("PROMOTION", mttr.details.path("markerKind").asText())
            }
        }
    }

    @Test
    fun `No promotion level gives NO_MARKER to the delivery readings`() {
        asAdmin {
            project {
                branch("main") {
                    build()
                }
                val readings = readings()
                ReadingKeys.ORDER.filter { it.startsWith("delivery.") }.forEach { key ->
                    val reading = readings.getValue(key)
                    assertEquals(ReadingBasis.UNKNOWN, reading.basis)
                    assertEquals(ReadingUnknownReason.NO_MARKER, reading.unknownReason)
                    assertNull(reading.value)
                    assertEquals("PROMOTION", reading.details.path("markerKind").asText())
                }
            }
        }
    }

    @Test
    fun `No promotion in the window gives NO_SAMPLES`() {
        asAdmin {
            project {
                branch("main") {
                    val gold = promotionLevel("GOLD")
                    build().apply {
                        updateBuildSignature(time = now.minusDays(200))
                        promote(gold, time = now.minusDays(150))
                    }
                }
                val readings = readings()
                listOf(
                    ReadingKeys.DELIVERY_LEAD_TIME,
                    ReadingKeys.DELIVERY_FREQUENCY,
                    ReadingKeys.DELIVERY_SUCCESS_RATE,
                ).forEach { key ->
                    val reading = readings.getValue(key)
                    assertEquals(ReadingBasis.UNKNOWN, reading.basis)
                    assertEquals(ReadingUnknownReason.NO_SAMPLES, reading.unknownReason)
                    assertEquals(0, reading.details.path("count").asInt())
                }
                // Nothing to restore
                val mttr = readings.getValue(ReadingKeys.DELIVERY_MTTR)
                assertEquals(ReadingBasis.UNKNOWN, mttr.basis)
                assertEquals(ReadingUnknownReason.NO_FAILURE, mttr.unknownReason)
                assertNull(mttr.value)
            }
        }
    }
}
