package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.samples.DurationSample
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LeadTimeReadingComputerTest {

    private val ref = LocalDateTime.of(2026, 9, 1, 12, 0)

    private fun sample(hours: Long, branchId: Int = 1) = DurationSample(
        branchId = branchId,
        start = ref,
        end = ref.plusHours(hours),
    )

    @Test
    fun `No sample gives an unknown reading`() {
        val outcome = LeadTimeReadingComputer.aggregate(emptyList())
        assertEquals(ReadingBasis.UNKNOWN, outcome.basis)
        assertEquals(ReadingUnknownReason.NO_SAMPLES, outcome.unknownReason)
        assertNull(outcome.value)
        assertEquals(0, outcome.details["count"])
    }

    @Test
    fun `Median in the value, the other statistics in the details`() {
        val outcome = LeadTimeReadingComputer.aggregate(
            listOf(sample(1), sample(2), sample(3), sample(4), sample(10, branchId = 2))
        )
        assertEquals(ReadingBasis.MEASURED, outcome.basis)
        assertNull(outcome.unknownReason)
        assertEquals(3 * 3600.0, outcome.value)
        assertEquals(5, outcome.details["count"])
        assertEquals(3600.0, outcome.details["min"])
        assertEquals(36000.0, outcome.details["max"])
        assertEquals(4 * 3600.0, outcome.details["mean"])
        assertEquals(36000.0, outcome.details["p90"])
    }

    @Test
    fun `One sample is enough`() {
        val outcome = LeadTimeReadingComputer.aggregate(listOf(sample(5)))
        assertEquals(ReadingBasis.MEASURED, outcome.basis)
        assertEquals(5 * 3600.0, outcome.value)
        assertEquals(1, outcome.details["count"])
    }

    @Test
    fun `Median of an even number of samples`() {
        val outcome = LeadTimeReadingComputer.aggregate(listOf(sample(1), sample(2), sample(4), sample(8)))
        assertEquals(3 * 3600.0, outcome.value)
    }
}
