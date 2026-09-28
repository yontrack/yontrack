package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.extension.chart.support.Interval
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InFlightTest {

    private val ref = LocalDateTime.of(2026, 9, 1, 0, 0)
    private val interval = Interval(ref, ref.plusDays(30))

    private fun leadTime(hours: Long) = DurationSample(1, ref, ref.plusHours(hours))

    @Test
    fun `Builds created within the median lead time before the end are in flight`() {
        val inFlight = InFlight.of(listOf(leadTime(1), leadTime(24), leadTime(48)), interval)
        assertEquals(86400.0, inFlight.leadTime)
        assertEquals(interval.end.minusDays(1), inFlight.since)
        assertTrue(interval.end.minusDays(1) in inFlight)
        assertTrue(interval.end.minusHours(1) in inFlight)
        assertFalse(interval.end.minusDays(1).minusSeconds(1) in inFlight)
    }

    @Test
    fun `No lead time, nothing in flight`() {
        val inFlight = InFlight.of(emptyList(), interval)
        assertNull(inFlight.leadTime)
        assertEquals(interval.end, inFlight.since)
        assertFalse(interval.end.minusSeconds(1) in inFlight)
    }

    @Test
    fun `Details of the exclusion`() {
        val inFlight = InFlight.of(listOf(leadTime(2)), interval)
        assertEquals(
            mapOf("leadTime" to 7200.0, "since" to interval.end.minusHours(2), "excluded" to 3),
            inFlight.details(excluded = 3)
        )
    }
}
