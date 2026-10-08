package net.nemerosa.ontrack.extension.findings.model

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FindingExposureEpisodeTest {

    private val main = 10
    private val release = 11
    private val stamp = 100
    private var nextId = 1

    private fun t(day: Int, hour: Int = 10) = LocalDateTime.of(2026, 10, day, hour, 0)

    private fun period(branchId: Int, startedAt: LocalDateTime, endedAt: LocalDateTime? = null) =
        FindingExposurePeriod(
            id = nextId++,
            findingId = 1,
            branchId = branchId,
            validationStampId = stamp,
            startedAt = startedAt,
            startedByValidationRunId = null,
            startedInBuild = null,
            endedAt = endedAt,
        )

    @Test
    fun `No period, no episode`() {
        assertTrue(FindingExposureEpisode.of(emptyList()).isEmpty())
    }

    @Test
    fun `A single ongoing period is one ongoing episode`() {
        val episodes = FindingExposureEpisode.of(listOf(period(main, t(2))))
        assertEquals(listOf(FindingExposureEpisode(start = t(2), end = null)), episodes)
        assertTrue(episodes.single().ongoing)
    }

    @Test
    fun `Overlapping periods on several branches merge into one episode`() {
        val episodes = FindingExposureEpisode.of(
            listOf(
                period(release, t(3), t(8)),
                period(main, t(2), t(6)),
            )
        )
        assertEquals(listOf(FindingExposureEpisode(start = t(2), end = t(8))), episodes)
        assertEquals(Duration.ofDays(6), episodes.single().duration(t(20)))
    }

    @Test
    fun `A period contained in another one adds nothing`() {
        val episodes = FindingExposureEpisode.of(
            listOf(
                period(main, t(2), t(10)),
                period(release, t(3), t(5)),
            )
        )
        assertEquals(listOf(FindingExposureEpisode(start = t(2), end = t(10))), episodes)
    }

    @Test
    fun `Touching periods merge`() {
        val episodes = FindingExposureEpisode.of(
            listOf(
                period(main, t(2), t(4)),
                period(release, t(4), t(6)),
            )
        )
        assertEquals(listOf(FindingExposureEpisode(start = t(2), end = t(6))), episodes)
    }

    @Test
    fun `A gap starts a new episode, with no tolerance`() {
        val episodes = FindingExposureEpisode.of(
            listOf(
                period(main, t(2), t(4)),
                period(main, t(4, hour = 11)),
            )
        )
        assertEquals(
            listOf(
                FindingExposureEpisode(start = t(2), end = t(4)),
                FindingExposureEpisode(start = t(4, hour = 11), end = null),
            ),
            episodes
        )
        assertEquals(Duration.ofHours(23), episodes.last().duration(t(5, hour = 10)))
    }

    @Test
    fun `An ongoing period keeps the episode it overlaps open`() {
        val episodes = FindingExposureEpisode.of(
            listOf(
                period(main, t(2), t(4)),
                period(release, t(3)),
                period(main, t(6), t(7)),
            )
        )
        assertEquals(listOf(FindingExposureEpisode(start = t(2), end = null)), episodes)
    }
}
