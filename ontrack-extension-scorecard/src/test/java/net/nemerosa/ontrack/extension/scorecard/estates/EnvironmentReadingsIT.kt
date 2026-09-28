package net.nemerosa.ontrack.extension.scorecard.estates

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.environments.Environment
import net.nemerosa.ontrack.extension.environments.EnvironmentsLicense
import net.nemerosa.ontrack.extension.environments.EnvironmentsLicensedFeatureProvider
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.service.EnvironmentService
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.license.DevLicenseService
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingEngine
import net.nemerosa.ontrack.extension.scorecard.model.EstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Delivery readings of an estate up to an environment, computed by the engine on backdated builds
 * and deployments.
 */
class EnvironmentReadingsIT : EstatesTestSupport() {

    @Autowired
    private lateinit var readingEngine: ReadingEngine

    @Autowired
    private lateinit var environmentService: EnvironmentService

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var environmentsLicense: EnvironmentsLicense

    @Autowired
    private lateinit var devLicenseService: DevLicenseService

    private val now = Time.now.withNano(0)

    private val deliveryKeys = ReadingKeys.ORDER.filter { it.startsWith("delivery.") }

    private fun environment(order: Int = 100): Environment =
        Environment(
            name = uid("env-"),
            order = order,
            description = null,
            image = false,
        ).apply {
            environmentService.save(this)
        }

    private fun Project.slot(environment: Environment, qualifier: String = Slot.DEFAULT_QUALIFIER): Slot =
        Slot(
            environment = environment,
            description = null,
            project = this,
            qualifier = qualifier,
        ).apply {
            slotService.addSlot(this)
        }

    /**
     * A build of the branch, created at [creation]
     */
    private fun Branch.buildAt(creation: LocalDateTime): Build {
        val build = build().apply { updateBuildSignature(time = creation) }
        return structureService.getBuild(build.id)
    }

    private fun Slot.done(build: Build, start: LocalDateTime, end: LocalDateTime) {
        val pipeline = running(build, start)
        assertTrue(slotService.finishDeployment(pipeline, dateTime = end).ok)
    }

    private fun Slot.failed(build: Build, start: LocalDateTime, end: LocalDateTime) {
        val pipeline = running(build, start)
        assertTrue(slotService.failPipeline(pipeline, message = "Failed", dateTime = end).ok)
    }

    private fun Slot.cancelled(build: Build, start: LocalDateTime, end: LocalDateTime) {
        val pipeline = slotService.startPipeline(this, build, dateTime = start)
        slotService.cancelPipeline(pipeline, reason = "Cancelled", dateTime = end)
    }

    private fun Slot.running(build: Build, start: LocalDateTime): String {
        val pipeline = slotService.startPipeline(this, build, dateTime = start)
        assertTrue(slotService.runDeployment(pipeline.id, dryRun = false, dateTime = start).ok)
        return pipeline.id
    }

    private fun Project.readings(estate: Estate): Map<String, Reading> =
        readingEngine.computeProject(EstateReadingSet(estate), this)!!.associateBy { it.key }

    private fun <T> withoutEnvironmentsLicence(code: () -> T): T {
        // The environments read their licence once: it is read before it is disabled for the test,
        // so that they keep it
        assertTrue(environmentsLicense.environmentFeatureEnabled)
        devLicenseService.setFeatureEnabled(EnvironmentsLicensedFeatureProvider.FEATURE_ENVIRONMENTS, false)
        return try {
            code()
        } finally {
            devLicenseService.setFeatureEnabled(EnvironmentsLicensedFeatureProvider.FEATURE_ENVIRONMENTS, true)
        }
    }

    @Test
    fun `Delivery readings up to the deployments of the slot with the default qualifier`() {
        asAdmin {
            val a = label()
            val env = environment()
            project {
                labels = listOf(a)
                val main = branch("main")
                val slot = slot(env)
                val blue = slot(env, qualifier = "blue")

                // Lead time of 26 hours, then redeployed
                val b1 = main.buildAt(now.minusDays(30))
                slot.done(b1, now.minusDays(29), now.minusDays(29).plusHours(2))
                slot.done(b1, now.minusDays(20), now.minusDays(20).plusHours(1))
                // Two failures in a row...
                val b2 = main.buildAt(now.minusDays(15))
                slot.failed(b2, now.minusDays(14), now.minusDays(14).plusHours(1))
                val b3 = main.buildAt(now.minusDays(13))
                slot.failed(b3, now.minusDays(13).plusHours(1), now.minusDays(13).plusHours(2))
                // ... restored 3 days after the first one, lead time of 25 hours
                val b4 = main.buildAt(now.minusDays(12))
                slot.done(b4, now.minusDays(11), now.minusDays(11).plusHours(1))
                // Cancelled, left out
                val b5 = main.buildAt(now.minusDays(5))
                slot.cancelled(b5, now.minusDays(4), now.minusDays(4).plusHours(1))
                // Another qualifier, not pooled
                blue.done(b1, now.minusDays(25), now.minusDays(25).plusHours(1))

                val estate = estate(a, marker = EstateEnvironmentMarker(env.name))
                val readings = readings(estate)

                readings.getValue(ReadingKeys.DELIVERY_LEAD_TIME).let {
                    assertEquals(ReadingBasis.MEASURED, it.basis)
                    assertEquals(25.5 * 3600, it.value)
                    assertEquals(2, it.details.path("count").asInt())
                    assertEquals(25 * 3600.0, it.details.path("min").asDouble())
                    assertEquals(26 * 3600.0, it.details.path("max").asDouble())
                    assertEquals("ENVIRONMENT", it.details.path("markerKind").asText())
                    assertEquals(env.name, it.details.path("marker").path("environment").asText())
                    assertEquals("", it.details.path("marker").path("qualifier").asText())
                }

                readings.getValue(ReadingKeys.DELIVERY_FREQUENCY).let {
                    assertEquals(ReadingBasis.MEASURED, it.basis)
                    assertEquals(3, it.details.path("count").asInt())
                    assertEquals(3 * 7.0 / 90.0, it.value!!, 1e-9)
                }

                readings.getValue(ReadingKeys.DELIVERY_SUCCESS_RATE).let {
                    assertEquals(ReadingBasis.MEASURED, it.basis)
                    assertEquals(60.0, it.value)
                    assertEquals(5, it.details.path("count").asInt())
                    assertEquals(3, it.details.path("done").asInt())
                    assertEquals(2, it.details.path("failed").asInt())
                    assertFalse(it.details.has("inFlight"))
                }

                readings.getValue(ReadingKeys.DELIVERY_MTTR).let {
                    assertEquals(ReadingBasis.MEASURED, it.basis)
                    assertEquals(3 * 86400.0, it.value)
                    assertEquals(1, it.details.path("count").asInt())
                    assertEquals(0, it.details.path("open").asInt())
                    assertFalse(it.details.has("inFlight"))
                }

                // The estate naming the other qualifier reads it only
                val blueReadings = readings(estate(a, marker = EstateEnvironmentMarker(env.name, qualifier = "blue")))
                blueReadings.getValue(ReadingKeys.DELIVERY_FREQUENCY).let {
                    assertEquals(1, it.details.path("count").asInt())
                    assertEquals("blue", it.details.path("marker").path("qualifier").asText())
                }
                blueReadings.getValue(ReadingKeys.DELIVERY_LEAD_TIME).let {
                    assertEquals((5 * 24 + 1) * 3600.0, it.value)
                }
                blueReadings.getValue(ReadingKeys.DELIVERY_SUCCESS_RATE).let {
                    assertEquals(100.0, it.value)
                }
                // Nothing to restore
                blueReadings.getValue(ReadingKeys.DELIVERY_MTTR).let {
                    assertEquals(ReadingBasis.UNKNOWN, it.basis)
                    assertEquals(ReadingUnknownReason.NO_FAILURE, it.unknownReason)
                    assertNull(it.value)
                }

                // The no-estate set stays on the promotion marker
                val noEstate = readingEngine.computeProject(NoEstateReadingSet, this)!!
                    .single { it.key == ReadingKeys.DELIVERY_FREQUENCY }
                assertEquals("PROMOTION", noEstate.details.path("markerKind").asText())
            }
        }
    }

    @Test
    fun `A failure not restored yet gives NO_SAMPLES to the time to restore`() {
        asAdmin {
            val a = label()
            val env = environment()
            project {
                labels = listOf(a)
                val main = branch("main")
                val slot = slot(env)
                val b1 = main.buildAt(now.minusDays(10))
                slot.done(b1, now.minusDays(9), now.minusDays(9).plusHours(1))
                val b2 = main.buildAt(now.minusDays(2))
                val failure = now.minusDays(1)
                slot.failed(b2, now.minusDays(1).minusHours(1), failure)

                val mttr = readings(estate(a, marker = EstateEnvironmentMarker(env.name)))
                    .getValue(ReadingKeys.DELIVERY_MTTR)
                assertEquals(ReadingBasis.UNKNOWN, mttr.basis)
                assertEquals(ReadingUnknownReason.NO_SAMPLES, mttr.unknownReason)
                assertEquals(1, mttr.details.path("open").asInt())
                assertEquals(failure, LocalDateTime.parse(mttr.details.path("openSince").asText().removeSuffix("Z")))
            }
        }
    }

    @Test
    fun `No deployment in the window gives NO_SAMPLES`() {
        asAdmin {
            val a = label()
            val env = environment()
            project {
                labels = listOf(a)
                branch("main")
                slot(env)
                val readings = readings(estate(a, marker = EstateEnvironmentMarker(env.name)))
                listOf(
                    ReadingKeys.DELIVERY_LEAD_TIME,
                    ReadingKeys.DELIVERY_FREQUENCY,
                    ReadingKeys.DELIVERY_SUCCESS_RATE,
                ).forEach { key ->
                    val reading = readings.getValue(key)
                    assertEquals(ReadingBasis.UNKNOWN, reading.basis, key)
                    assertEquals(ReadingUnknownReason.NO_SAMPLES, reading.unknownReason, key)
                    assertEquals("ENVIRONMENT", reading.details.path("markerKind").asText())
                }
                assertEquals(ReadingUnknownReason.NO_FAILURE, readings.getValue(ReadingKeys.DELIVERY_MTTR).unknownReason)
            }
        }
    }

    @Test
    fun `No slot in the environment, or no such environment, gives NO_MARKER`() {
        asAdmin {
            val a = label()
            val env = environment()
            project {
                labels = listOf(a)
                branch("main")
                // A slot for another qualifier only
                slot(env, qualifier = "blue")
                listOf(
                    estate(a, marker = EstateEnvironmentMarker(env.name)),
                    estate(a, marker = EstateEnvironmentMarker(uid("unknown-"))),
                ).forEach { estate ->
                    val readings = readings(estate)
                    deliveryKeys.forEach { key ->
                        val reading = readings.getValue(key)
                        assertEquals(ReadingBasis.UNKNOWN, reading.basis, key)
                        assertEquals(ReadingUnknownReason.NO_MARKER, reading.unknownReason, key)
                        assertEquals("ENVIRONMENT", reading.details.path("markerKind").asText())
                    }
                }
            }
        }
    }

    @Test
    fun `With no marker, an estate reads up to the highest-ordered environment where the project owns a slot`() {
        asAdmin {
            val a = label()
            val staging = environment(order = 10)
            val production = environment(order = 20)
            // Higher, but with another qualifier only
            val dr = environment(order = 30)
            project {
                labels = listOf(a)
                val main = branch("main")
                slot(staging)
                val slot = slot(production)
                slot(dr, qualifier = "blue")
                val b1 = main.buildAt(now.minusDays(3))
                slot.done(b1, now.minusDays(2), now.minusDays(2).plusHours(1))

                val frequency = readings(estate(a)).getValue(ReadingKeys.DELIVERY_FREQUENCY)
                assertEquals(ReadingBasis.MEASURED, frequency.basis)
                assertEquals(1, frequency.details.path("count").asInt())
                assertEquals("ENVIRONMENT", frequency.details.path("markerKind").asText())
                assertEquals(production.name, frequency.details.path("marker").path("environment").asText())
            }
        }
    }

    @Test
    fun `Without the environments licence, the delivery readings up to an environment are NOT_LICENSED`() {
        asAdmin {
            val a = label()
            val env = environment()
            project {
                labels = listOf(a)
                val main = branch("main")
                val slot = slot(env)
                val b1 = main.buildAt(now.minusDays(3))
                slot.done(b1, now.minusDays(2), now.minusDays(2).plusHours(1))
                val named = estate(a, marker = EstateEnvironmentMarker(env.name))
                val default = estate(a)

                withoutEnvironmentsLicence {
                    listOf(named, default).forEach { estate ->
                        val readings = readings(estate)
                        deliveryKeys.forEach { key ->
                            val reading = readings.getValue(key)
                            assertEquals(ReadingBasis.UNKNOWN, reading.basis, key)
                            assertEquals(ReadingUnknownReason.NOT_LICENSED, reading.unknownReason, key)
                            assertEquals("ENVIRONMENT", reading.details.path("markerKind").asText())
                        }
                        // The test readings do not depend on the marker
                        assertEquals(
                            ReadingUnknownReason.NO_TEST_STAMP,
                            readings.getValue(ReadingKeys.QUALITY_TEST_PASS_RATE).unknownReason
                        )
                    }
                }

                // Back with the licence
                assertEquals(
                    ReadingBasis.MEASURED,
                    readings(named).getValue(ReadingKeys.DELIVERY_FREQUENCY).basis
                )
            }
        }
    }
}
