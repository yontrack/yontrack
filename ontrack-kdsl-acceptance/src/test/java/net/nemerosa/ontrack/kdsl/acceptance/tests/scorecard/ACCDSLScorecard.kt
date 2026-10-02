package net.nemerosa.ontrack.kdsl.acceptance.tests.scorecard

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.ReadingBasis
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.ReadingDirection
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.ReadingUnknownReason
import net.nemerosa.ontrack.kdsl.spec.createLabel
import net.nemerosa.ontrack.kdsl.spec.extension.environments.environments
import net.nemerosa.ontrack.kdsl.spec.extension.environments.startPipeline
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.EstateMarker
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.EstateReadingConfig
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.Reading
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.ReadingKeys
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.ScorecardSet
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.estates
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.recomputeScorecardAndWait
import net.nemerosa.ontrack.kdsl.spec.extension.scorecard.scorecard
import net.nemerosa.ontrack.kdsl.spec.setLabels
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading the delivery scorecard of a project through the KDSL, for both markers, from backdated
 * builds, promotions and deployments.
 */
class ACCDSLScorecard : AbstractACCDSLTestSupport() {

    private val ref = LocalDateTime.now(ZoneOffset.UTC).minusDays(30).withNano(0)

    @Test
    fun `Promotion marker, with no estate and in an estate`() {
        val label = ontrack.createLabel(category = uid("scorecard-"), name = "product")
        val project = project {
            setLabels(label)
            branch("main") {
                promotion("SILVER")
                promotion("GOLD")
                // Promoted
                build("1") {
                    updateCreationTime(ref)
                    promote("SILVER", dateTime = ref.plusHours(2))
                    promote("GOLD", dateTime = ref.plusHours(10))
                }
                // Not promoted: an outage starts
                build("2") {
                    updateCreationTime(ref.plusDays(1))
                }
                // Promoted: the outage ends
                build("3") {
                    updateCreationTime(ref.plusDays(2))
                    promote("SILVER", dateTime = ref.plusDays(2).plusHours(4))
                    promote("GOLD", dateTime = ref.plusDays(2).plusHours(20))
                }
            }
            this
        }
        val estate = ontrack.estates.create(
            name = uid("Products "),
            labels = listOf(label.display),
            marker = EstateMarker.Promotion("SILVER"),
            readings = listOf(
                EstateReadingConfig(key = ReadingKeys.DELIVERY_LEAD_TIME, target = 2.hours),
                EstateReadingConfig(key = ReadingKeys.DELIVERY_FREQUENCY, windowDays = 60),
                EstateReadingConfig(key = ReadingKeys.DELIVERY_SUCCESS_RATE, target = 50.0),
            ),
        )
        try {
            val scorecard = project.recomputeScorecardAndWait()
            assertEquals(listOf("Project", estate.name), scorecard.sets.map { it.name })

            // With no estate, up to the last promotion level, GOLD, with no target
            with(scorecard.noEstate) {
                assertNull(this.estate)
                assertMarker("PROMOTION", mapOf("levels" to mapOf("main" to "GOLD")))
                assertMeasured(ReadingKeys.DELIVERY_LEAD_TIME, 15.hours, count = 2) // 10h and 20h
                assertFrequency(count = 2)
                assertMeasured(ReadingKeys.DELIVERY_SUCCESS_RATE, 200.0 / 3, count = 3)
                assertMeasured(ReadingKeys.DELIVERY_MTTR, 44.hours, count = 1) // 2 creation -> 3 GOLD
                assertUnknown(ReadingKeys.QUALITY_TEST_PASS_RATE, ReadingUnknownReason.NO_TEST_STAMP)
                assertUnknown(ReadingKeys.QUALITY_TEST_FLAKINESS, ReadingUnknownReason.NO_TEST_STAMP)
                // No finding at all
                assertUnknown(ReadingKeys.SECURITY_REMEDIATION_TIME, ReadingUnknownReason.NO_SAMPLES)
                // No remediation target with no estate
                assertUnknown(ReadingKeys.SECURITY_OVERDUE, ReadingUnknownReason.NO_TARGET)
                readings.forEach {
                    assertNull(it.target)
                    assertNull(it.targetMet)
                }
            }

            // In the estate, up to SILVER, judged against the targets
            assertNotNull(scorecard.estate(estate.name)) { set ->
                set.assertMarker("PROMOTION", mapOf("levels" to mapOf("main" to "SILVER")))
                set.assertMeasured(ReadingKeys.DELIVERY_LEAD_TIME, 3.hours, count = 2) { // 2h and 4h
                    assertEquals(ReadingDirection.LOWER_IS_BETTER, it.direction)
                    assertEquals(2.hours, it.target)
                    assertEquals(false, it.targetMet)
                }
                // Over the window of the estate
                set.assertFrequency(count = 2) {
                    assertEquals(Duration.ofDays(60), Duration.between(it.windowStart, it.windowEnd))
                }
                set.assertMeasured(ReadingKeys.DELIVERY_SUCCESS_RATE, 200.0 / 3, count = 3) {
                    assertEquals(50.0, it.target)
                    assertEquals(true, it.targetMet)
                }
                set.assertMeasured(ReadingKeys.DELIVERY_MTTR, 28.hours, count = 1) // 2 creation -> 3 SILVER
            }

            // History of the readings: today's snapshot
            val history = project.scorecard(historyDays = 7).noEstate.reading(ReadingKeys.DELIVERY_LEAD_TIME)?.history
            assertEquals(1, history?.size)
        } finally {
            estate.delete()
        }
    }

    @Test
    fun `Environment marker, named and by default`() {
        val label = ontrack.createLabel(category = uid("scorecard-"), name = "production")
        val environment = ontrack.environments.createEnvironment(name = uid("production-"), order = 0)
        val project = project {
            setLabels(label)
            val slot = environment.createSlot(project = this)
            branch("main") {
                // Deployed: lead time 3h
                val build1 = build(name = "1") { this }.updateCreationTime(ref)
                build1.startPipeline(slot, dateTime = ref.plusHours(1))
                    .startDeploying(dateTime = ref.plusHours(2))
                    .finishDeployment(dateTime = ref.plusHours(3))
                // Failed: an outage starts
                val build2 = build(name = "2") { this }.updateCreationTime(ref.plusDays(1))
                slot.createPipeline(build2, dateTime = ref.plusDays(1).plusHours(1))
                    .startDeploying(dateTime = ref.plusDays(1).plusHours(2))
                    .fail(message = "Smoke tests failed", dateTime = ref.plusDays(1).plusHours(3))
                // Cancelled: left out
                val build3 = build(name = "3") { this }.updateCreationTime(ref.plusDays(2))
                slot.createPipeline(build3, dateTime = ref.plusDays(2).plusHours(1))
                    .cancel(reason = "Not needed", dateTime = ref.plusDays(2).plusHours(2))
                // Deployed: lead time 5h, the outage ends
                val build4 = build(name = "4") { this }.updateCreationTime(ref.plusDays(3))
                slot.createPipeline(build4, dateTime = ref.plusDays(3).plusHours(1))
                    .startDeploying(dateTime = ref.plusDays(3).plusHours(2))
                    .finishDeployment(dateTime = ref.plusDays(3).plusHours(5))
                // Redeployed: one more deployment, no new lead time
                slot.createPipeline(build1, dateTime = ref.plusDays(4))
                    .startDeploying(dateTime = ref.plusDays(4).plusHours(1))
                    .finishDeployment(dateTime = ref.plusDays(4).plusHours(2))
            }
            this
        }

        val named = ontrack.estates.create(
            name = uid("Production "),
            labels = listOf(label.display),
            marker = EstateMarker.Environment(environment.name),
            readings = listOf(
                EstateReadingConfig(key = ReadingKeys.DELIVERY_MTTR, target = 1.days),
            ),
        )
        val byDefault = ontrack.estates.create(
            name = uid("Default "),
            labels = listOf(label.display),
        )
        try {
            // Recomputing the estate only
            val estateScorecards = named.recomputeAndWait()
            assertEquals(setOf(project.name), estateScorecards.keys)

            val scorecard = project.recomputeScorecardAndWait()

            // Both estates, whose marker is the environment
            listOf(named, byDefault).forEach { estate ->
                assertNotNull(scorecard.estate(estate.name)) { set ->
                    set.assertMarker("ENVIRONMENT", mapOf("environment" to environment.name, "qualifier" to ""))
                    set.assertMeasured(ReadingKeys.DELIVERY_LEAD_TIME, 4.hours, count = 2) // 3h and 5h
                    set.assertFrequency(count = 3)
                    set.assertMeasured(ReadingKeys.DELIVERY_SUCCESS_RATE, 75.0, count = 4) {
                        assertEquals(3, it.details?.path("done")?.asInt())
                        assertEquals(1, it.details?.path("failed")?.asInt())
                    }
                    set.assertMeasured(ReadingKeys.DELIVERY_MTTR, 50.hours, count = 1) // 2 failed -> 4 done
                }
            }
            // Judged in the named estate only
            assertEquals(false, scorecard.estate(named.name)?.reading(ReadingKeys.DELIVERY_MTTR)?.targetMet)
            assertNull(scorecard.estate(byDefault.name)?.reading(ReadingKeys.DELIVERY_MTTR)?.targetMet)

            // With no estate, no promotion level
            with(scorecard.noEstate) {
                assertEquals("PROMOTION", reading(ReadingKeys.DELIVERY_LEAD_TIME)?.details?.path("markerKind")?.asString())
                listOf(
                    ReadingKeys.DELIVERY_LEAD_TIME,
                    ReadingKeys.DELIVERY_FREQUENCY,
                    ReadingKeys.DELIVERY_SUCCESS_RATE,
                    ReadingKeys.DELIVERY_MTTR,
                ).forEach { assertUnknown(it, ReadingUnknownReason.NO_MARKER) }
            }
        } finally {
            named.delete()
            byDefault.delete()
        }
    }

    private val Int.hours: Double get() = Duration.ofHours(toLong()).toSeconds().toDouble()
    private val Int.days: Double get() = Duration.ofDays(toLong()).toSeconds().toDouble()

    private fun ScorecardSet.assertMarker(kind: String, marker: Map<String, Any>) {
        val details = assertNotNull(reading(ReadingKeys.DELIVERY_LEAD_TIME)?.details, "Lead time details")
        assertEquals(kind, details.path("markerKind").asString())
        assertEquals(marker.asJson(), details.path("marker"))
    }

    private fun ScorecardSet.assertMeasured(
        key: String,
        value: Double,
        count: Int,
        code: (Reading) -> Unit = {},
    ) {
        val reading = assertNotNull(reading(key), "Reading $key")
        assertEquals(ReadingBasis.MEASURED, reading.basis, "Basis of $key")
        assertNull(reading.unknownReason)
        assertEquals(value, assertNotNull(reading.value), 0.01, "Value of $key")
        assertEquals(count, reading.details?.path("count")?.asInt(), "Count of $key")
        code(reading)
    }

    /**
     * Frequency: the count normalised per week of the window
     */
    private fun ScorecardSet.assertFrequency(count: Int, code: (Reading) -> Unit = {}) {
        val reading = assertNotNull(reading(ReadingKeys.DELIVERY_FREQUENCY))
        val weeks = Duration.between(reading.windowStart, reading.windowEnd).toSeconds() / 7.days
        assertMeasured(ReadingKeys.DELIVERY_FREQUENCY, count / weeks, count, code)
    }

    private fun ScorecardSet.assertUnknown(key: String, reason: ReadingUnknownReason) {
        val reading = assertNotNull(reading(key), "Reading $key")
        assertEquals(ReadingBasis.UNKNOWN, reading.basis, "Basis of $key")
        assertEquals(reason, reading.unknownReason, "Unknown reason of $key")
        assertNull(reading.value)
        assertTrue(reading.targetMet == null)
    }
}
