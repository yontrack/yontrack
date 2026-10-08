package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.findings.model.FindingExposureEpisode
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.samples.SecurityFindingSample
import net.nemerosa.ontrack.extension.scorecard.samples.SecurityTargets
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `security.overdue`, on plain lists of findings.
 */
class SecurityOverdueReadingComputerTest {

    private val end = LocalDateTime.of(2026, 10, 1, 2, 0)
    private val window = Interval(end.minusDays(90), end)

    /**
     * CRITICAL fixed within 7 days, HIGH within 30
     */
    private val targets = SecurityTargets(criticalDays = 7, highDays = 30)

    private var nextId = 1

    private fun finding(
        severity: FindingSeverity,
        daysAgo: Long,
        state: FindingState = FindingState.OPEN,
    ) = SecurityFindingSample(
        findingId = nextId++,
        severity = severity,
        episodes = listOf(
            FindingExposureEpisode(
                start = end.minusDays(daysAgo),
                end = if (state == FindingState.RESOLVED) end.minusDays(1) else null,
            )
        ),
        state = state,
    )

    private val findings = listOf(
        // Overdue CRITICAL, one of them first seen before the window
        finding(FindingSeverity.CRITICAL, daysAgo = 8),
        finding(FindingSeverity.CRITICAL, daysAgo = 200),
        // CRITICAL within its target
        finding(FindingSeverity.CRITICAL, daysAgo = 6),
        // Overdue HIGH
        finding(FindingSeverity.HIGH, daysAgo = 31),
        // HIGH within its target
        finding(FindingSeverity.HIGH, daysAgo = 8),
        // Old, but not open
        finding(FindingSeverity.CRITICAL, daysAgo = 100, state = FindingState.ACCEPTED),
        finding(FindingSeverity.HIGH, daysAgo = 100, state = FindingState.RESOLVED),
    )

    @Test
    fun `Open CRITICAL older than the CRITICAL target plus open HIGH older than the HIGH target`() {
        val outcome = SecurityOverdueReadingComputer.aggregate(window, targets, findings)
        assertEquals(ReadingBasis.MEASURED, outcome.basis)
        assertNull(outcome.unknownReason)
        assertEquals(3.0, outcome.value)
        assertEquals(2, outcome.details["overdueCritical"])
        assertEquals(1, outcome.details["overdueHigh"])
        assertEquals(3, outcome.details["openCritical"])
        assertEquals(2, outcome.details["openHigh"])
        assertEquals(7, outcome.details["criticalTargetDays"])
        assertEquals(30, outcome.details["highTargetDays"])
        assertEquals(1, outcome.details["accepted"])
        assertEquals(end.minusDays(200), outcome.details["overdueSince"])
    }

    @Test
    fun `Nothing open reads 0`() {
        val outcome = SecurityOverdueReadingComputer.aggregate(
            window,
            targets,
            listOf(finding(FindingSeverity.CRITICAL, daysAgo = 100, state = FindingState.RESOLVED)),
        )
        assertEquals(ReadingBasis.MEASURED, outcome.basis)
        assertEquals(0.0, outcome.value)
        assertNull(outcome.details["overdueSince"])
    }

    @Test
    fun `A finding exactly as old as its target is not overdue yet`() {
        val outcome = SecurityOverdueReadingComputer.aggregate(
            window,
            targets,
            listOf(finding(FindingSeverity.CRITICAL, daysAgo = 7)),
        )
        assertEquals(0.0, outcome.value)
    }

    @Test
    fun `No target gives NO_TARGET, with the open and accepted counts`() {
        val outcome = SecurityOverdueReadingComputer.aggregate(window, SecurityTargets.NONE, findings)
        assertEquals(ReadingBasis.UNKNOWN, outcome.basis)
        assertEquals(ReadingUnknownReason.NO_TARGET, outcome.unknownReason)
        assertNull(outcome.value)
        assertEquals(3, outcome.details["openCritical"])
        assertEquals(2, outcome.details["openHigh"])
        assertEquals(1, outcome.details["accepted"])
        assertNull(outcome.details["criticalTargetDays"])
        assertNull(outcome.details["highTargetDays"])
    }

    @Test
    fun `With a CRITICAL target only, the HIGH findings are not judged`() {
        val outcome = SecurityOverdueReadingComputer.aggregate(
            window,
            SecurityTargets(criticalDays = 7, highDays = null),
            findings,
        )
        assertEquals(ReadingBasis.MEASURED, outcome.basis)
        assertEquals(2.0, outcome.value)
        assertEquals(2, outcome.details["overdueCritical"])
        assertNull(outcome.details["overdueHigh"])
        assertEquals(2, outcome.details["openHigh"])
    }

    @Test
    fun `With a HIGH target only, the CRITICAL findings are not judged`() {
        val outcome = SecurityOverdueReadingComputer.aggregate(
            window,
            SecurityTargets(criticalDays = null, highDays = 30),
            findings,
        )
        assertEquals(1.0, outcome.value)
        assertNull(outcome.details["overdueCritical"])
        assertEquals(1, outcome.details["overdueHigh"])
    }

    @Test
    fun `A target of 0 days makes every open finding overdue`() {
        val outcome = SecurityOverdueReadingComputer.aggregate(
            window,
            SecurityTargets(criticalDays = 0, highDays = 0),
            findings,
        )
        assertEquals(5.0, outcome.value)
    }

    @Test
    fun `The age of a reopened finding runs from the start of its current episode`() {
        val reopened = SecurityFindingSample(
            findingId = nextId++,
            severity = FindingSeverity.CRITICAL,
            episodes = listOf(
                FindingExposureEpisode(start = end.minusDays(200), end = end.minusDays(150)),
                FindingExposureEpisode(start = end.minusDays(9), end = end.minusDays(5)),
                FindingExposureEpisode(start = end.minusDays(3), end = null),
            ),
            state = FindingState.OPEN,
        )
        assertEquals(0.0, SecurityOverdueReadingComputer.aggregate(window, targets, listOf(reopened)).value)
        val overdue = reopened.copy(
            episodes = reopened.episodes.dropLast(1) + FindingExposureEpisode(start = end.minusDays(4), end = null)
        )
        val outcome = SecurityOverdueReadingComputer.aggregate(
            window,
            SecurityTargets(criticalDays = 3, highDays = null),
            listOf(overdue),
        )
        assertEquals(1.0, outcome.value)
        assertEquals(end.minusDays(4), outcome.details["overdueSince"])
    }
}
