package net.nemerosa.ontrack.extension.findings.query

import net.nemerosa.ontrack.extension.findings.model.FindingExposure
import net.nemerosa.ontrack.extension.findings.model.FindingExposureEpisode
import net.nemerosa.ontrack.extension.findings.model.FindingExposurePeriod
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FindingExposedForTest {

    private val main = 10
    private val release = 11
    private val scan = 100
    private val image = 101
    private val today = LocalDate.of(2026, 10, 20)
    private var nextId = 1

    private fun t(day: Int) = LocalDateTime.of(2026, 10, day, 10, 0)

    private fun period(
        branchId: Int,
        startedAt: LocalDateTime,
        endedAt: LocalDateTime? = null,
        stampId: Int = scan,
    ) = FindingExposurePeriod(
        id = nextId++,
        findingId = 1,
        branchId = branchId,
        validationStampId = stampId,
        startedAt = startedAt,
        startedByValidationRunId = null,
        startedInBuild = null,
        endedAt = endedAt,
    )

    private fun exposure(
        branchId: Int,
        since: LocalDateTime,
        stampId: Int = scan,
        accepted: Boolean = false,
        acceptanceExpiresAt: LocalDate? = null,
        resolvedAt: LocalDateTime? = null,
    ) = FindingExposure(
        findingId = 1,
        branchId = branchId,
        validationStampId = stampId,
        since = since,
        accepted = accepted,
        acceptanceExpiresAt = acceptanceExpiresAt,
        resolvedAt = resolvedAt,
    )

    @Test
    fun `No period, nothing to show`() {
        val exposedFor = FindingExposedFor.of(emptyList(), emptyList(), today)
        assertNull(exposedFor.ongoing)
        assertNull(exposedFor.lastEpisode)
    }

    @Test
    fun `The longest ongoing period over the branches and stamps`() {
        val oldest = period(release, t(3), stampId = image)
        val exposedFor = FindingExposedFor.of(
            periods = listOf(
                period(main, t(1), t(2)),
                period(main, t(5)),
                oldest,
            ),
            exposures = listOf(
                exposure(main, t(5)),
                exposure(release, t(3), stampId = image),
            ),
            date = today,
        )
        assertEquals(oldest, exposedFor.ongoing)
        assertFalse(exposedFor.accepted)
        assertFalse(exposedFor.reopened)
        // One episode from day 1 to day 2, the current one from day 3
        assertEquals(FindingExposureEpisode(start = t(3), end = null), exposedFor.lastEpisode)
    }

    @Test
    fun `Accepted when the acceptance of the exposure of the ongoing period holds`() {
        val exposedFor = FindingExposedFor.of(
            periods = listOf(period(main, t(1))),
            exposures = listOf(exposure(main, t(1), accepted = true, acceptanceExpiresAt = today)),
            date = today,
        )
        assertTrue(exposedFor.accepted)
        val expired = FindingExposedFor.of(
            periods = listOf(period(main, t(1))),
            exposures = listOf(exposure(main, t(1), accepted = true, acceptanceExpiresAt = today.minusDays(1))),
            date = today,
        )
        assertFalse(expired.accepted)
    }

    @Test
    fun `Reopened when the ongoing period follows an earlier one of the same branch and stamp`() {
        val exposedFor = FindingExposedFor.of(
            periods = listOf(
                period(main, t(1), t(2)),
                period(main, t(4)),
            ),
            exposures = listOf(exposure(main, t(4))),
            date = today,
        )
        assertTrue(exposedFor.reopened)
        assertEquals(FindingExposureEpisode(start = t(4), end = null), exposedFor.lastEpisode)
    }

    @Test
    fun `An earlier period on another stamp does not make a reopening`() {
        val exposedFor = FindingExposedFor.of(
            periods = listOf(
                period(main, t(1), t(2), stampId = image),
                period(main, t(4)),
            ),
            exposures = listOf(exposure(main, t(4))),
            date = today,
        )
        assertFalse(exposedFor.reopened)
    }

    @Test
    fun `A fixed finding has no ongoing period, and its last episode`() {
        val exposedFor = FindingExposedFor.of(
            periods = listOf(
                period(main, t(1), t(2)),
                period(main, t(4), t(9)),
                period(release, t(6), t(10)),
            ),
            exposures = listOf(
                exposure(main, t(4), resolvedAt = t(9)),
                exposure(release, t(6), resolvedAt = t(10)),
            ),
            date = today,
        )
        assertNull(exposedFor.ongoing)
        assertFalse(exposedFor.accepted)
        assertEquals(FindingExposureEpisode(start = t(4), end = t(10)), exposedFor.lastEpisode)
    }
}
