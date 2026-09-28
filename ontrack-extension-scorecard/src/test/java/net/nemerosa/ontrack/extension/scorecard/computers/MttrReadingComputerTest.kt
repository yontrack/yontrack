package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.samples.BuildSample
import net.nemerosa.ontrack.extension.scorecard.samples.DurationSample
import net.nemerosa.ontrack.extension.scorecard.samples.InFlight
import net.nemerosa.ontrack.extension.scorecard.samples.OutageSample
import net.nemerosa.ontrack.extension.scorecard.samples.Outages
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MttrReadingComputerTest {

    private val ref = LocalDateTime.of(2026, 9, 1, 0, 0)
    private val window = Interval(ref, ref.plusDays(30))

    private val noneInFlight = InFlight.of(emptyList(), window)

    /**
     * Builds in flight for a median lead time of [hours]
     */
    private fun inFlight(hours: Long) = InFlight.of(listOf(DurationSample(1, ref, ref.plusHours(hours))), window)

    private fun restored(days: Long, branchId: Int = 1) =
        OutageSample(branchId = branchId, start = ref.plusDays(1), restored = ref.plusDays(1 + days))

    @Test
    fun `B1 promoted, B2 to B4 unpromoted, B5 promoted`() {
        val builds = listOf(
            BuildSample(1, 1, ref.plusDays(1), ref.plusDays(1).plusHours(1)),
            BuildSample(1, 2, ref.plusDays(2), null),
            BuildSample(1, 3, ref.plusDays(3), null),
            BuildSample(1, 4, ref.plusDays(4), null),
            BuildSample(1, 5, ref.plusDays(5), ref.plusDays(6)),
        )
        val outcome = MttrReadingComputer.aggregate(Outages.of(builds, window), noneInFlight)
        assertEquals(ReadingBasis.MEASURED, outcome.basis)
        // B2 creation to B5 promotion
        assertEquals(4 * 86400.0, outcome.value)
        assertEquals(1, outcome.details["count"])
        assertEquals(0, outcome.details["open"])
    }

    @Test
    fun `Median of the times to restore in the value, the other statistics in the details`() {
        val outcome = MttrReadingComputer.aggregate(
            listOf(restored(1), restored(2), restored(3), restored(10, branchId = 2)),
            noneInFlight,
        )
        assertEquals(ReadingBasis.MEASURED, outcome.basis)
        assertEquals(2.5 * 86400, outcome.value)
        assertEquals(4, outcome.details["count"])
        assertEquals(86400.0, outcome.details["min"])
        assertEquals(10 * 86400.0, outcome.details["max"])
        assertEquals(4 * 86400.0, outcome.details["mean"])
    }

    @Test
    fun `No outage gives NO_FAILURE, never 0`() {
        val outcome = MttrReadingComputer.aggregate(emptyList(), noneInFlight)
        assertEquals(ReadingBasis.UNKNOWN, outcome.basis)
        assertEquals(ReadingUnknownReason.NO_FAILURE, outcome.unknownReason)
        assertNull(outcome.value)
        assertEquals(0, outcome.details["count"])
        assertEquals(0, outcome.details["open"])
    }

    @Test
    fun `An outage still going on and nothing restored gives NO_SAMPLES`() {
        val start = window.end.minusDays(5)
        val outcome = MttrReadingComputer.aggregate(
            listOf(OutageSample(1, start, null)),
            inFlight(24),
        )
        assertEquals(ReadingBasis.UNKNOWN, outcome.basis)
        assertEquals(ReadingUnknownReason.NO_SAMPLES, outcome.unknownReason)
        assertNull(outcome.value)
        assertEquals(0, outcome.details["count"])
        assertEquals(1, outcome.details["open"])
        assertEquals(start, outcome.details["openSince"])
    }

    @Test
    fun `An outage started by a build in flight is not a failure yet`() {
        val outcome = MttrReadingComputer.aggregate(
            listOf(OutageSample(1, window.end.minusHours(2), null)),
            inFlight(24),
        )
        assertEquals(ReadingUnknownReason.NO_FAILURE, outcome.unknownReason)
        assertEquals(0, outcome.details["open"])
        assertNull(outcome.details["openSince"])
        assertEquals(
            mapOf("leadTime" to 86400.0, "since" to window.end.minusHours(24), "excluded" to 1),
            outcome.details["inFlight"]
        )
    }

    @Test
    fun `Outages restored in the window are measured even with an outage still going on`() {
        val start = window.end.minusDays(5)
        val outcome = MttrReadingComputer.aggregate(
            listOf(restored(2), OutageSample(1, start, null)),
            noneInFlight,
        )
        assertEquals(ReadingBasis.MEASURED, outcome.basis)
        assertEquals(2 * 86400.0, outcome.value)
        assertEquals(1, outcome.details["count"])
        assertEquals(1, outcome.details["open"])
        assertEquals(start, outcome.details["openSince"])
    }

    @Test
    fun `With no builds in flight to leave out, the details say nothing about them`() {
        val start = window.end.minusHours(2)
        val outcome = MttrReadingComputer.aggregate(
            listOf(restored(2), OutageSample(1, start, null)),
            inFlight = null,
        )
        assertEquals(ReadingBasis.MEASURED, outcome.basis)
        assertEquals(2 * 86400.0, outcome.value)
        // A recent failure is a failure: nothing is in flight
        assertEquals(1, outcome.details["open"])
        assertEquals(start, outcome.details["openSince"])
        assertFalse("inFlight" in outcome.details)
    }

    @Test
    fun `With no builds in flight, an outage still going on gives NO_SAMPLES`() {
        val outcome = MttrReadingComputer.aggregate(
            listOf(OutageSample(1, window.end.minusHours(2), null)),
            inFlight = null,
        )
        assertEquals(ReadingUnknownReason.NO_SAMPLES, outcome.unknownReason)
        assertEquals(1, outcome.details["open"])
    }
}
