package net.nemerosa.ontrack.extension.findings.query

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.findings.model.FindingExposurePeriod
import net.nemerosa.ontrack.extension.findings.model.FindingPeriodBoundaryStatus
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.model.structure.StandardBuildFilterData
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationRunStatus
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FindingPeriodBoundaryComputationTest {

    private val start = LocalDateTime.of(2026, 10, 2, 12, 0)
    private val end = start.plusDays(4)
    private val now = start.plusDays(18)
    private val build = mockk<Build>()

    private val anyFilter = StandardBuildFilterData.of(10)

    private fun period(endedAt: LocalDateTime? = end) = FindingExposurePeriod(
        id = 1,
        findingId = 1,
        branchId = 1,
        validationStampId = 1,
        startedAt = start,
        startedByValidationRunId = 1,
        startedInBuild = "3.4.0",
        endedAt = endedAt,
        endedByValidationRunId = endedAt?.let { 2 },
        endedInBuild = endedAt?.let { "3.4.2" },
    )

    private fun outcome(
        period: FindingExposurePeriod = period(),
        hasNextPeriod: Boolean = false,
        notApplicable: String? = null,
        match: Pair<Build, LocalDateTime>? = null,
    ) = FindingPeriodBoundaryComputation.boundary(
        period = period,
        hasNextPeriod = hasNextPeriod,
        notApplicable = notApplicable,
        match = match,
        now = now,
    )

    @Test
    fun `An open period has no boundary`() {
        assertNull(outcome(period = period(endedAt = null)))
    }

    @Test
    fun `A matching build gives the build, the time, and the duration from the start of the period`() {
        val boundary = outcome(match = build to start.plusDays(12))!!
        assertEquals(FindingPeriodBoundaryStatus.MATCHED, boundary.status)
        assertEquals(build, boundary.build)
        assertEquals(start.plusDays(12), boundary.time)
        assertEquals(12 * 86400L, boundary.durationSeconds)
        assertNull(boundary.reason)
    }

    @Test
    fun `The boundary on the fixing build itself, scanned after its promotion, is at the end of the period`() {
        val boundary = outcome(match = build to end.minusDays(1))!!
        assertEquals(FindingPeriodBoundaryStatus.MATCHED, boundary.status)
        assertEquals(end, boundary.time)
        assertEquals(4 * 86400L, boundary.durationSeconds)
    }

    @Test
    fun `No match and no later period is none yet, its duration running to now`() {
        val boundary = outcome()!!
        assertEquals(FindingPeriodBoundaryStatus.NONE_YET, boundary.status)
        assertNull(boundary.build)
        assertNull(boundary.time)
        assertEquals(18 * 86400L, boundary.durationSeconds)
        assertNull(boundary.reason)
    }

    @Test
    fun `No match before the next period is not reached, without any duration`() {
        val boundary = outcome(hasNextPeriod = true)!!
        assertEquals(FindingPeriodBoundaryStatus.NOT_REACHED, boundary.status)
        assertNull(boundary.build)
        assertNull(boundary.time)
        assertNull(boundary.durationSeconds)
        assertNull(boundary.reason)
    }

    @Test
    fun `A filter which cannot apply says why, without any build, time or duration`() {
        val boundary = outcome(notApplicable = "No GOLD on this branch", hasNextPeriod = true)!!
        assertEquals(FindingPeriodBoundaryStatus.NOT_APPLICABLE, boundary.status)
        assertEquals("No GOLD on this branch", boundary.reason)
        assertNull(boundary.build)
        assertNull(boundary.time)
        assertNull(boundary.durationSeconds)
    }

    @Test
    fun `A since criterion cannot set a boundary, and neither count nor the usable criteria say so`() {
        listOf(
            anyFilter.withSincePromotionLevel("GOLD"),
            anyFilter.withSinceValidationStamp("SCAN"),
            anyFilter.withSinceValidationStampStatus("PASSED"),
            anyFilter.withSinceProperty("some.Property"),
            anyFilter.withSincePropertyValue("value"),
        ).forEach { filter ->
            assertEquals(FindingPeriodBoundaryComputation.SINCE_NOT_USABLE, FindingPeriodBoundaryComputation.sinceCriterionReason(filter))
        }
        assertNull(FindingPeriodBoundaryComputation.sinceCriterionReason(anyFilter))
        assertNull(FindingPeriodBoundaryComputation.sinceCriterionReason(anyFilter.withWithPromotionLevel("GOLD")))
    }

    private val created = start.plusDays(5)
    private val promoted = start.plusDays(7)
    private val validated = start.plusDays(9)

    private fun matchingSince(filter: StandardBuildFilterData) =
        FindingPeriodBoundaryComputation.matchingSince(filter, created, promoted, validated)

    @Test
    fun `With a promotion, the build started to match when it was promoted`() {
        assertEquals(promoted, matchingSince(anyFilter.withWithPromotionLevel("GOLD")))
    }

    @Test
    fun `With a validation, the build started to match when it was validated`() {
        assertEquals(validated, matchingSince(anyFilter.withWithValidationStamp("SCAN")))
    }

    @Test
    fun `With any other criterion, or none, the build matched from its creation`() {
        listOf(
            anyFilter,
            anyFilter.withWithDisplayName("^3\\."),
            anyFilter.withWithProperty("some.Property"),
            anyFilter.withLinkedFrom("other:*"),
            anyFilter.withLinkedTo("other:*"),
            anyFilter.withAfterDate(start.toLocalDate()),
            anyFilter.withBeforeDate(start.toLocalDate()),
            anyFilter.withAssisted("YES"),
            anyFilter.withActor("HUMAN"),
        ).forEach { filter ->
            assertEquals(created, matchingSince(filter), "$filter")
        }
    }

    @Test
    fun `With several criteria, the latest of their times`() {
        assertEquals(
            validated,
            matchingSince(anyFilter.withWithPromotionLevel("GOLD").withWithValidationStamp("SCAN")),
        )
        // A promotion on a build created later than it
        assertEquals(
            promoted,
            FindingPeriodBoundaryComputation.matchingSince(
                anyFilter.withWithPromotionLevel("GOLD").withWithDisplayName("3"),
                created = promoted.minusDays(1),
                promoted = promoted,
                validated = null,
            ),
        )
        assertEquals(
            promoted.plusDays(1),
            FindingPeriodBoundaryComputation.matchingSince(
                anyFilter.withWithPromotionLevel("GOLD").withWithDisplayName("3"),
                created = promoted.plusDays(1),
                promoted = promoted,
                validated = null,
            ),
        )
    }

    private fun status(id: String, time: LocalDateTime) = mockk<ValidationRunStatus> {
        every { statusID } returns mockk<ValidationRunStatusID> { every { this@mockk.id } returns id }
        every { signature } returns mockk<Signature> { every { this@mockk.time } returns time }
    }

    /** The statuses of a run, the most recent first, as a run keeps them. */
    private fun run(vararg statuses: ValidationRunStatus) = mockk<ValidationRun> {
        every { validationRunStatuses } returns statuses.toList()
    }

    @Test
    fun `Without a status, the first run of the stamp is when the build was validated`() {
        val first = start.plusDays(1)
        val second = start.plusDays(2)
        assertEquals(
            first,
            FindingPeriodBoundaryComputation.validatedSince(
                runs = listOf(run(status("PASSED", second)), run(status("FAILED", first))),
                status = null,
            ),
        )
    }

    @Test
    fun `With a status, the first time a run of the stamp had it`() {
        val failed = start.plusDays(1)
        val passed = start.plusDays(2)
        val fixed = start.plusDays(3)
        assertEquals(
            passed,
            FindingPeriodBoundaryComputation.validatedSince(
                runs = listOf(run(status("PASSED", passed)), run(status("FAILED", failed))),
                status = "PASSED",
            ),
        )
        // On one run, whose status changed
        assertEquals(
            fixed,
            FindingPeriodBoundaryComputation.validatedSince(
                runs = listOf(run(status("FIXED", fixed), status("FAILED", failed))),
                status = "FIXED",
            ),
        )
    }

    @Test
    fun `Without any run, there is no time of validation`() {
        assertNull(FindingPeriodBoundaryComputation.validatedSince(emptyList(), null))
    }
}
