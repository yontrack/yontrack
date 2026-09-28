package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.samples.EventSample
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrequencyReadingComputerTest {

    private val end = LocalDateTime.of(2026, 9, 29, 2, 0)

    private fun window(days: Long) = Interval(end.minusDays(days), end)

    private fun events(count: Int) = (1..count).map {
        EventSample(branchId = 1, time = end.minusHours(it.toLong()))
    }

    @Test
    fun `No promotion gives an unknown reading`() {
        val outcome = FrequencyReadingComputer.aggregate(emptyList(), window(90))
        assertEquals(ReadingBasis.UNKNOWN, outcome.basis)
        assertEquals(ReadingUnknownReason.NO_SAMPLES, outcome.unknownReason)
        assertNull(outcome.value)
        assertEquals(0, outcome.details["count"])
    }

    @Test
    fun `Promotions per week, raw count in the details`() {
        val outcome = FrequencyReadingComputer.aggregate(events(14), window(28))
        assertEquals(ReadingBasis.MEASURED, outcome.basis)
        assertEquals(3.5, outcome.value)
        assertEquals(14, outcome.details["count"])
    }

    @Test
    fun `Normalised on a window which is not a whole number of weeks`() {
        val outcome = FrequencyReadingComputer.aggregate(events(9), window(90))
        assertEquals(9 * 7.0 / 90.0, outcome.value!!, 1e-9)
        assertEquals(9, outcome.details["count"])
    }
}
