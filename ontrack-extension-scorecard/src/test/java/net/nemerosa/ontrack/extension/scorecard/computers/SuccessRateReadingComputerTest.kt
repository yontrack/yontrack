package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.samples.BuildSample
import net.nemerosa.ontrack.extension.scorecard.samples.DeploymentSample
import net.nemerosa.ontrack.extension.scorecard.samples.DurationSample
import net.nemerosa.ontrack.extension.scorecard.samples.InFlight
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SuccessRateReadingComputerTest {

    private val ref = LocalDateTime.of(2026, 9, 1, 0, 0)
    private val window = Interval(ref, ref.plusDays(30))

    private var nextId = 1

    /**
     * Build created [hoursBeforeEnd] before the end of the window
     */
    private fun build(hoursBeforeEnd: Long, promoted: Boolean) = window.end.minusHours(hoursBeforeEnd).let { creation ->
        BuildSample(
            branchId = 1,
            buildId = nextId++,
            creation = creation,
            promotion = if (promoted) creation.plusMinutes(30) else null,
        )
    }

    /**
     * Builds in flight for a median lead time of [hours]
     */
    private fun inFlight(hours: Long) = InFlight.of(listOf(DurationSample(1, ref, ref.plusHours(hours))), window)

    @Test
    fun `Share of the builds promoted, as a percentage`() {
        val outcome = SuccessRateReadingComputer.aggregate(
            listOf(
                build(100, promoted = true),
                build(90, promoted = false),
                build(80, promoted = true),
                build(70, promoted = true),
            ),
            inFlight(24),
        )
        assertEquals(ReadingBasis.MEASURED, outcome.basis)
        assertNull(outcome.unknownReason)
        assertEquals(75.0, outcome.value)
        assertEquals(4, outcome.details["count"])
        assertEquals(3, outcome.details["promoted"])
    }

    @Test
    fun `Builds in flight are left out, and the exclusion stated in the details`() {
        val outcome = SuccessRateReadingComputer.aggregate(
            listOf(
                build(100, promoted = true),
                build(50, promoted = false),
                // Created within the median lead time (24 hours) before the end
                build(24, promoted = false),
                build(10, promoted = false),
                build(1, promoted = true),
            ),
            inFlight(24),
        )
        assertEquals(50.0, outcome.value)
        assertEquals(2, outcome.details["count"])
        assertEquals(1, outcome.details["promoted"])
        assertEquals(
            mapOf("leadTime" to 86400.0, "since" to window.end.minusHours(24), "excluded" to 3),
            outcome.details["inFlight"]
        )
    }

    @Test
    fun `A build created just before the in-flight limit is counted`() {
        val outcome = SuccessRateReadingComputer.aggregate(
            listOf(
                BuildSample(1, 1, window.end.minusHours(24).minusSeconds(1), null),
            ),
            inFlight(24),
        )
        assertEquals(0.0, outcome.value)
        assertEquals(1, outcome.details["count"])
    }

    @Test
    fun `No lead time, no build in flight`() {
        val outcome = SuccessRateReadingComputer.aggregate(
            listOf(build(100, promoted = false), build(1, promoted = false)),
            InFlight.of(emptyList(), window),
        )
        assertEquals(ReadingBasis.MEASURED, outcome.basis)
        assertEquals(0.0, outcome.value)
        assertEquals(2, outcome.details["count"])
        assertEquals(
            mapOf("leadTime" to null, "since" to window.end, "excluded" to 0),
            outcome.details["inFlight"]
        )
    }

    @Test
    fun `Every build in flight gives NO_SAMPLES`() {
        val outcome = SuccessRateReadingComputer.aggregate(
            listOf(build(10, promoted = true), build(2, promoted = false)),
            inFlight(24),
        )
        assertEquals(ReadingBasis.UNKNOWN, outcome.basis)
        assertEquals(ReadingUnknownReason.NO_SAMPLES, outcome.unknownReason)
        assertNull(outcome.value)
        assertEquals(0, outcome.details["count"])
        assertEquals(
            mapOf("leadTime" to 86400.0, "since" to window.end.minusHours(24), "excluded" to 2),
            outcome.details["inFlight"]
        )
    }

    @Test
    fun `No build gives NO_SAMPLES`() {
        val outcome = SuccessRateReadingComputer.aggregate(emptyList(), InFlight.of(emptyList(), window))
        assertEquals(ReadingBasis.UNKNOWN, outcome.basis)
        assertEquals(ReadingUnknownReason.NO_SAMPLES, outcome.unknownReason)
        assertEquals(0, outcome.details["count"])
    }

    // ---------------------------------------------------------------------------------------------
    // Environment marker
    // ---------------------------------------------------------------------------------------------

    private fun deployment(day: Long, failed: Boolean) = DeploymentSample(
        branchId = 1,
        number = nextId++,
        end = ref.plusDays(day),
        failed = failed,
    )

    @Test
    fun `Deployments done over deployments done or failed`() {
        val outcome = SuccessRateReadingComputer.aggregateDeployments(
            listOf(
                deployment(1, failed = false),
                deployment(2, failed = true),
                deployment(3, failed = false),
                deployment(4, failed = false),
            )
        )
        assertEquals(ReadingBasis.MEASURED, outcome.basis)
        assertEquals(75.0, outcome.value)
        assertEquals(mapOf("count" to 4, "done" to 3, "failed" to 1), outcome.details)
    }

    @Test
    fun `No deployment done or failed gives NO_SAMPLES`() {
        val outcome = SuccessRateReadingComputer.aggregateDeployments(emptyList())
        assertEquals(ReadingBasis.UNKNOWN, outcome.basis)
        assertEquals(ReadingUnknownReason.NO_SAMPLES, outcome.unknownReason)
        assertNull(outcome.value)
        assertEquals(mapOf("count" to 0, "done" to 0, "failed" to 0), outcome.details)
    }
}
