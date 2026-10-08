package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.findings.model.FindingExposureEpisode
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.samples.SecurityFindingSample
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `security.remediationTime`, on plain lists of findings.
 */
class SecurityRemediationTimeReadingComputerTest {

    private val end = LocalDateTime.of(2026, 10, 1, 2, 0)
    private val window = Interval(end.minusDays(90), end)

    private var nextId = 1

    /**
     * A finding exposed from some days before the end of the window, fixed some days after
     */
    private fun resolved(
        firstSeenDaysAgo: Long,
        fixedInDays: Long,
        severity: FindingSeverity = FindingSeverity.CRITICAL,
    ) = SecurityFindingSample(
        findingId = nextId++,
        severity = severity,
        episodes = listOf(episode(firstSeenDaysAgo, fixedInDays)),
        state = FindingState.RESOLVED,
    )

    private fun episode(startDaysAgo: Long, lengthDays: Long?) = FindingExposureEpisode(
        start = end.minusDays(startDaysAgo),
        end = lengthDays?.let { end.minusDays(startDaysAgo).plusDays(it) },
    )

    private fun notResolved(state: FindingState, severity: FindingSeverity = FindingSeverity.HIGH) =
        SecurityFindingSample(
            findingId = nextId++,
            severity = severity,
            episodes = listOf(episode(10, null)),
            state = state,
        )

    @Test
    fun `No finding resolved in the window gives NO_SAMPLES`() {
        val outcome = SecurityRemediationTimeReadingComputer.aggregate(
            window,
            listOf(
                notResolved(FindingState.OPEN),
                // Resolved before the window
                resolved(firstSeenDaysAgo = 200, fixedInDays = 5),
            )
        )
        assertEquals(ReadingBasis.UNKNOWN, outcome.basis)
        assertEquals(ReadingUnknownReason.NO_SAMPLES, outcome.unknownReason)
        assertNull(outcome.value)
        assertEquals(0, outcome.details["count"])
    }

    @Test
    fun `Median of the episodes ending in the window, the other statistics in the details`() {
        val outcome = SecurityRemediationTimeReadingComputer.aggregate(
            window,
            listOf(
                resolved(firstSeenDaysAgo = 50, fixedInDays = 1),
                resolved(firstSeenDaysAgo = 50, fixedInDays = 2, severity = FindingSeverity.HIGH),
                resolved(firstSeenDaysAgo = 40, fixedInDays = 3),
                // Started before the window, ended in it
                resolved(firstSeenDaysAgo = 120, fixedInDays = 40, severity = FindingSeverity.HIGH),
                // Ended before the window: not a sample
                resolved(firstSeenDaysAgo = 150, fixedInDays = 10),
                notResolved(FindingState.OPEN),
            )
        )
        assertEquals(ReadingBasis.MEASURED, outcome.basis)
        assertNull(outcome.unknownReason)
        val day = 86400.0
        assertEquals(2.5 * day, outcome.value)
        assertEquals(4, outcome.details["count"])
        assertEquals(1 * day, outcome.details["min"])
        assertEquals(40 * day, outcome.details["max"])
        assertEquals(11.5 * day, outcome.details["mean"])
        assertEquals(40 * day, outcome.details["p90"])
    }

    @Test
    fun `The accepted findings are counted apart, neither open nor resolved`() {
        val outcome = SecurityRemediationTimeReadingComputer.aggregate(
            window,
            listOf(
                resolved(firstSeenDaysAgo = 20, fixedInDays = 4),
                notResolved(FindingState.ACCEPTED),
                notResolved(FindingState.ACCEPTED, severity = FindingSeverity.CRITICAL),
                notResolved(FindingState.OPEN),
            )
        )
        assertEquals(4 * 86400.0, outcome.value)
        assertEquals(1, outcome.details["count"])
        assertEquals(2, outcome.details["accepted"])
    }

    @Test
    fun `The accepted count is given when the reading is unknown`() {
        val outcome = SecurityRemediationTimeReadingComputer.aggregate(
            window,
            listOf(notResolved(FindingState.ACCEPTED)),
        )
        assertEquals(ReadingUnknownReason.NO_SAMPLES, outcome.unknownReason)
        assertEquals(1, outcome.details["accepted"])
    }

    @Test
    fun `A reopened finding gives one sample per episode ending in the window, the gap excluded`() {
        val outcome = SecurityRemediationTimeReadingComputer.aggregate(
            window,
            listOf(
                SecurityFindingSample(
                    findingId = nextId++,
                    severity = FindingSeverity.HIGH,
                    episodes = listOf(
                        // Ended before the window: not a sample
                        episode(200, 10),
                        episode(60, 7),
                        episode(47, 3),
                        // Ongoing: not a sample
                        episode(5, null),
                    ),
                    state = FindingState.OPEN,
                )
            )
        )
        val day = 86400.0
        assertEquals(2, outcome.details["count"])
        assertEquals(3 * day, outcome.details["min"])
        assertEquals(7 * day, outcome.details["max"])
        assertEquals(5 * day, outcome.value)
    }
}
