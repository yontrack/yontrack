package net.nemerosa.ontrack.extension.scorecard.engine

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.metrics.ScorecardMetrics
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.settings.ScorecardSettings
import net.nemerosa.ontrack.extension.scorecard.storage.ReadingRepository
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReadingEngineTest {

    private val meterRegistry = SimpleMeterRegistry()
    private val readingRepository = mockk<ReadingRepository>(relaxed = true)
    private val cachedSettingsService = mockk<CachedSettingsService> {
        every { getCachedSettings(ScorecardSettings::class.java) } returns ScorecardSettings(windowDays = 30)
    }
    private val readingsListener = mockk<ReadingsListener>(relaxed = true)

    private fun project(id: Int) = mockk<Project> {
        every { id() } returns id
        every { name } returns "P$id"
    }

    private val resolver = mockk<ReadingSubjectResolver> {
        every { resolve(any(), any()) } answers {
            ReadingSubject(
                set = firstArg(),
                project = secondArg(),
                scope = ReadingScope(ReadingScopeKind.ALL_BRANCHES, emptyList()),
                markerKind = MarkerKind.PROMOTION,
                marker = null,
            )
        }
    }

    private class FixedComputer(
        override val key: String,
        private val failingProjects: Set<Int> = emptySet(),
    ) : ReadingComputer {
        var lastWindow: Interval? = null
        override fun compute(subject: ReadingSubject, window: Interval): ReadingOutcome {
            lastWindow = window
            if (subject.project.id() in failingProjects) error("Boom")
            return ReadingOutcome.measured(1.0, mapOf("count" to 1))
        }
    }

    private fun engine(vararg computers: ReadingComputer) = ReadingEngine(
        readingSubjectResolver = resolver,
        computers = computers.toList(),
        readingRepository = readingRepository,
        cachedSettingsService = cachedSettingsService,
        meterRegistry = meterRegistry,
        readingsListeners = listOf(readingsListener),
    )

    @Test
    fun `Readings in the catalogue order, with the common details, over the window of the settings`() {
        val frequency = FixedComputer(ReadingKeys.DELIVERY_FREQUENCY)
        val leadTime = FixedComputer(ReadingKeys.DELIVERY_LEAD_TIME)
        val saved = slot<List<Reading>>()
        every { readingRepository.save(capture(saved)) } returns Unit

        val readings = engine(frequency, leadTime).computeProject(NoEstateReadingSet, project(1))

        assertNotNull(readings)
        assertEquals(listOf(ReadingKeys.DELIVERY_LEAD_TIME, ReadingKeys.DELIVERY_FREQUENCY), readings.map { it.key })
        assertEquals(readings, saved.captured)
        val reading = readings.first()
        assertNull(reading.estateId)
        assertEquals(1, reading.projectId)
        assertEquals(1.0, reading.value)
        assertEquals(ReadingBasis.MEASURED, reading.basis)
        assertEquals(reading.computedAt, reading.windowEnd)
        assertEquals(reading.computedAt.toLocalDate(), reading.day)
        assertEquals(Duration.ofDays(30), Duration.between(reading.windowStart, reading.windowEnd))
        assertEquals(Interval(reading.windowStart, reading.windowEnd), leadTime.lastWindow)
        assertEquals("PROMOTION", reading.details.path("markerKind").asText())
        assertEquals(true, reading.details.path("marker").isNull)
        assertEquals("ALL_BRANCHES", reading.details.path("scope").path("kind").asText())
        assertEquals(1, reading.details.path("count").asInt())
        verify { readingsListener.onReadings(NoEstateReadingSet, any(), readings) }
    }

    @Test
    fun `Unknown readings keep their reason`() {
        val computer = object : ReadingComputer {
            override val key = ReadingKeys.DELIVERY_LEAD_TIME
            override fun compute(subject: ReadingSubject, window: Interval) =
                ReadingOutcome.unknown(ReadingUnknownReason.NO_MARKER)
        }
        val reading = engine(computer).computeProject(NoEstateReadingSet, project(1))!!.single()
        assertEquals(ReadingBasis.UNKNOWN, reading.basis)
        assertEquals(ReadingUnknownReason.NO_MARKER, reading.unknownReason)
        assertNull(reading.value)
    }

    @Test
    fun `A failing project gets no reading, is counted, and does not stop the others`() {
        val saved = mutableListOf<List<Reading>>()
        every { readingRepository.save(capture(saved)) } returns Unit
        val engine = engine(
            FixedComputer(ReadingKeys.DELIVERY_LEAD_TIME, failingProjects = setOf(2)),
            FixedComputer(ReadingKeys.DELIVERY_FREQUENCY),
        )

        val failures = engine.computeSet(NoEstateReadingSet, listOf(project(1), project(2), project(3)))

        assertEquals(1, failures)
        assertEquals(listOf(1, 3), saved.map { it.first().projectId })
        assertEquals(
            1.0,
            meterRegistry.counter(ScorecardMetrics.errors, ScorecardMetrics.Tags.ESTATE, "-").count()
        )
        assertEquals(
            3,
            meterRegistry.timer(ScorecardMetrics.computation, ScorecardMetrics.Tags.ESTATE, "-").count().toInt()
        )
        verify(exactly = 2) { readingsListener.onReadings(any(), any(), any()) }
    }
}
