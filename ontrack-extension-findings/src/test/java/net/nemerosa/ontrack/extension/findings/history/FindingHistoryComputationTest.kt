package net.nemerosa.ontrack.extension.findings.history

import net.nemerosa.ontrack.extension.findings.model.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FindingHistoryComputationTest {

    private val finding = 1
    private val main = 10
    private val release = 11
    private val stamp = 100
    private val today = LocalDate.of(2026, 10, 8)

    private fun t(day: Int, hour: Int = 10) = LocalDateTime.of(2026, 10, day, hour, 0)

    @Test
    fun `Discovery, observations and the fix`() {
        val periods = listOf(period(1, main, t(2), run = 1, build = "3.4.0", endedAt = t(6), endRun = 5, endBuild = "3.4.2"))
        val sightings = listOf(
            sighting(main, t(2), run = 1),
            sighting(main, t(3), run = 2),
            sighting(main, t(4), run = 3),
            sighting(main, t(5), run = 4),
        )
        val history = compute(periods, sightings)
        assertEquals(
            listOf(
                FindingHistoryEntryType.RESOLVED,
                FindingHistoryEntryType.OBSERVATIONS,
                FindingHistoryEntryType.DISCOVERED,
            ),
            history.map { it.type }
        )
        val resolved = history[0]
        assertEquals(t(6), resolved.time)
        assertEquals(5, resolved.validationRunId)
        assertEquals("3.4.2", resolved.build)
        assertEquals(FindingResolutionReason.ABSENT, resolved.resolutionReason)
        val group = history[1]
        assertEquals(3, group.count)
        assertEquals(t(3), group.firstTime)
        assertEquals(t(5), group.lastTime)
        assertEquals(t(5), group.time)
        assertEquals(2, group.firstValidationRunId)
        assertEquals(4, group.lastValidationRunId)
        val discovered = history[2]
        assertEquals(t(2), discovered.time)
        assertEquals(1, discovered.validationRunId)
        assertEquals("3.4.0", discovered.build)
        assertEquals(main, discovered.branchId)
    }

    @Test
    fun `Only the earliest period of the finding is its discovery, the others are exposures or reopenings`() {
        val periods = listOf(
            period(1, main, t(2), run = 1, endedAt = t(3), endRun = 2),
            period(2, release, t(2, 12), run = 10),
            period(3, main, t(5), run = 3),
        )
        val sightings = listOf(
            sighting(main, t(2), run = 1),
            sighting(release, t(2, 12), run = 10),
            sighting(main, t(5), run = 3),
        )
        val history = compute(periods, sightings)
        assertEquals(
            listOf(
                FindingHistoryEntryType.REOPENED to main,
                FindingHistoryEntryType.RESOLVED to main,
                FindingHistoryEntryType.EXPOSED to release,
                FindingHistoryEntryType.DISCOVERED to main,
            ),
            history.map { it.type to it.branchId }
        )
    }

    @Test
    fun `An acceptance opens and is withdrawn, splitting the observations`() {
        val periods = listOf(period(1, main, t(1), run = 1))
        val sightings = listOf(
            sighting(main, t(1), run = 1),
            sighting(main, t(2), run = 2),
            sighting(main, t(3), run = 3, acceptance = acceptance()),
            sighting(main, t(4), run = 4, acceptance = acceptance()),
            sighting(main, t(5), run = 5),
            sighting(main, t(6), run = 6),
        )
        val history = compute(periods, sightings)
        assertEquals(
            listOf(
                FindingHistoryEntryType.OBSERVATIONS,
                FindingHistoryEntryType.ACCEPTANCE_WITHDRAWN,
                FindingHistoryEntryType.OBSERVATIONS,
                FindingHistoryEntryType.ACCEPTED,
                FindingHistoryEntryType.OBSERVATIONS,
                FindingHistoryEntryType.DISCOVERED,
            ),
            history.map { it.type }
        )
        assertEquals(listOf(1, 1, 1), history.filter { it.type == FindingHistoryEntryType.OBSERVATIONS }.map { it.count })
        val accepted = history[3]
        assertEquals(t(3), accepted.time)
        assertEquals(3, accepted.validationRunId)
        assertEquals("Not reachable", accepted.acceptance?.statement)
        assertEquals(t(5), history[1].time)
        assertEquals(5, history[1].validationRunId)
    }

    @Test
    fun `An acceptance past its expiry is expired on the day after its last one, when read`() {
        val periods = listOf(period(1, main, t(1), run = 1))
        val sightings = listOf(
            sighting(main, t(1), run = 1, acceptance = acceptance(expiresAt = LocalDate.of(2026, 10, 5))),
            sighting(main, t(3), run = 2, acceptance = acceptance(expiresAt = LocalDate.of(2026, 10, 5))),
        )
        val history = compute(periods, sightings)
        assertEquals(
            listOf(
                FindingHistoryEntryType.ACCEPTANCE_EXPIRED,
                FindingHistoryEntryType.OBSERVATIONS,
                FindingHistoryEntryType.ACCEPTED,
                FindingHistoryEntryType.DISCOVERED,
            ),
            history.map { it.type }
        )
        val expired = history[0]
        assertEquals(LocalDateTime.of(2026, 10, 6, 0, 0), expired.time)
        assertNull(expired.validationRunId)
        assertEquals(LocalDate.of(2026, 10, 5), expired.acceptance?.expiresAt)
    }

    @Test
    fun `A finding discovered accepted has its discovery and its acceptance at the same time`() {
        val periods = listOf(period(1, main, t(1), run = 1))
        val sightings = listOf(sighting(main, t(1), run = 1, acceptance = acceptance()))
        val history = compute(periods, sightings)
        assertEquals(
            listOf(FindingHistoryEntryType.ACCEPTED, FindingHistoryEntryType.DISCOVERED),
            history.map { it.type }
        )
    }

    @Test
    fun `An acceptance which has not expired yet produces no expiry`() {
        val periods = listOf(period(1, main, t(1), run = 1))
        val sightings = listOf(sighting(main, t(1), run = 1, acceptance = acceptance(expiresAt = today)))
        val history = compute(periods, sightings)
        assertEquals(
            listOf(FindingHistoryEntryType.ACCEPTED, FindingHistoryEntryType.DISCOVERED),
            history.map { it.type }
        )
    }

    @Test
    fun `An acceptance expiring after the end of its period produces no expiry`() {
        val periods = listOf(period(1, main, t(1), run = 1, endedAt = t(2), endRun = 2))
        val sightings = listOf(sighting(main, t(1), run = 1, acceptance = acceptance(expiresAt = LocalDate.of(2026, 10, 4))))
        val history = compute(periods, sightings)
        assertEquals(
            listOf(FindingHistoryEntryType.RESOLVED, FindingHistoryEntryType.ACCEPTED, FindingHistoryEntryType.DISCOVERED),
            history.map { it.type }
        )
    }

    @Test
    fun `Accepted spans of a period`() {
        val p = period(1, main, t(1), run = 1)
        val sightings = listOf(
            sighting(main, t(1), run = 1),
            sighting(main, t(2), run = 2, acceptance = acceptance()),
            sighting(main, t(3), run = 3),
            sighting(main, t(4), run = 4, acceptance = acceptance(expiresAt = LocalDate.of(2026, 10, 5))),
        )
        val spans = FindingHistoryComputation.acceptedSpans(p, sightings, today)
        assertEquals(
            listOf(
                Triple(t(2), t(3), 2),
                Triple(t(4), LocalDateTime.of(2026, 10, 6, 0, 0), 4),
            ),
            spans.map { Triple(it.from, it.to, it.fromValidationRunId) }
        )
    }

    @Test
    fun `An accepted span still holding is open`() {
        val p = period(1, main, t(1), run = 1)
        val spans = FindingHistoryComputation.acceptedSpans(p, listOf(sighting(main, t(1), run = 1, acceptance = acceptance())), today)
        assertEquals(1, spans.size)
        assertEquals(t(1), spans.single().from)
        assertNull(spans.single().to)
    }

    @Test
    fun `An accepted span is closed by the end of its period`() {
        val p = period(1, main, t(1), run = 1, endedAt = t(2), endRun = 2)
        val spans = FindingHistoryComputation.acceptedSpans(p, listOf(sighting(main, t(1), run = 1, acceptance = acceptance())), today)
        assertEquals(t(2), spans.single().to)
    }

    @Test
    fun `A period without run, older than the periods, still swallows its first observation`() {
        val periods = listOf(period(1, main, t(1), run = null, build = null))
        val sightings = listOf(sighting(main, t(1), run = 1), sighting(main, t(2), run = 2))
        val history = compute(periods, sightings)
        assertEquals(
            listOf(FindingHistoryEntryType.OBSERVATIONS, FindingHistoryEntryType.DISCOVERED),
            history.map { it.type }
        )
        assertEquals(1, history[0].count)
        assertNull(history[1].validationRunId)
        assertNull(history[1].build)
    }

    @Test
    fun `Observations outside any period are grouped on their own`() {
        val periods = listOf(period(1, main, t(5), run = 3))
        val sightings = listOf(
            sighting(main, t(1), run = 1),
            sighting(main, t(2), run = 2),
            sighting(main, t(5), run = 3),
        )
        val history = compute(periods, sightings)
        assertEquals(
            listOf(FindingHistoryEntryType.DISCOVERED, FindingHistoryEntryType.OBSERVATIONS),
            history.map { it.type }
        )
        assertEquals(2, history[1].count)
    }

    @Test
    fun `The observations of the stamps of a branch are not mixed`() {
        val periods = listOf(
            period(1, main, t(1), run = 1),
            period(2, main, t(1, 11), run = 10, stamp = 101),
        )
        val sightings = listOf(
            sighting(main, t(1), run = 1),
            sighting(main, t(1, 11), run = 10, stamp = 101),
            sighting(main, t(2), run = 2),
            sighting(main, t(2, 11), run = 11, stamp = 101),
        )
        val history = compute(periods, sightings)
        assertEquals(
            listOf(
                FindingHistoryEntryType.OBSERVATIONS to 101,
                FindingHistoryEntryType.OBSERVATIONS to stamp,
                FindingHistoryEntryType.EXPOSED to 101,
                FindingHistoryEntryType.DISCOVERED to stamp,
            ),
            history.map { it.type to it.validationStampId }
        )
    }

    private fun compute(periods: List<FindingExposurePeriod>, sightings: List<FindingObservationSighting>) =
        FindingHistoryComputation.history(periods, sightings, today)

    private fun period(
        id: Int,
        branch: Int,
        startedAt: LocalDateTime,
        run: Int?,
        build: String? = run?.let { "build-$it" },
        endedAt: LocalDateTime? = null,
        endRun: Int? = null,
        endBuild: String? = endRun?.let { "build-$it" },
        stamp: Int = this.stamp,
    ) = FindingExposurePeriod(
        id = id,
        findingId = finding,
        branchId = branch,
        validationStampId = stamp,
        startedAt = startedAt,
        startedByValidationRunId = run,
        startedInBuild = build,
        endedAt = endedAt,
        endedByValidationRunId = endRun,
        endedInBuild = endBuild,
        resolutionReason = endedAt?.let { FindingResolutionReason.ABSENT },
    )

    private fun sighting(
        branch: Int,
        time: LocalDateTime,
        run: Int,
        acceptance: FindingAcceptance? = null,
        stamp: Int = this.stamp,
    ) = FindingObservationSighting(
        observation = FindingObservation(
            findingId = finding,
            validationRunId = run,
            time = time,
            severity = FindingSeverity.HIGH,
            rawSeverity = null,
            installedVersion = null,
            fixedVersion = null,
            acceptance = acceptance,
        ),
        branchId = branch,
        validationStampId = stamp,
    )

    private fun acceptance(expiresAt: LocalDate? = null) = FindingAcceptance(
        statement = "Not reachable",
        expiresAt = expiresAt,
        source = null,
    )
}
