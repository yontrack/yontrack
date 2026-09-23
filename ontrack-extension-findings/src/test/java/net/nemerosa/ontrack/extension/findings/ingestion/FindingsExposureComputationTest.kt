package net.nemerosa.ontrack.extension.findings.ingestion

import net.nemerosa.ontrack.extension.findings.model.FindingExposure
import net.nemerosa.ontrack.extension.findings.model.FindingExposureState
import net.nemerosa.ontrack.extension.findings.model.FindingResolutionReason
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

class FindingsExposureComputationTest {

    private val branch = 10
    private val stamp = 100
    private val otherStamp = 101
    private val t0 = LocalDateTime.of(2026, 9, 20, 10, 0)
    private val t1 = LocalDateTime.of(2026, 9, 23, 10, 0)

    @Test
    fun `A first scan exposes what it reports`() {
        val change = compute(emptyList(), reported(1), reported(2))
        assertEquals(
            listOf(exposure(1, since = t1), exposure(2, since = t1)),
            change.saved
        )
        assertEquals(listOf(new(1), new(2)), change.transitions)
    }

    @Test
    fun `A finding still reported keeps its exposure and changes nothing`() {
        val change = compute(listOf(exposure(1)), reported(1))
        assertEquals(emptyList(), change.saved)
        assertEquals(emptyList(), change.transitions)
    }

    @Test
    fun `A finding no longer reported is resolved as absent`() {
        val change = compute(listOf(exposure(1), exposure(2)), reported(2))
        assertEquals(
            listOf(exposure(1).copy(resolvedAt = t1, resolutionReason = FindingResolutionReason.ABSENT)),
            change.saved
        )
        assertEquals(listOf(resolved(1)), change.transitions)
    }

    @Test
    fun `A resolved finding stays resolved when still not reported`() {
        val change = compute(listOf(exposure(1).resolved()), *emptyArray())
        assertEquals(emptyList(), change.saved)
        assertEquals(emptyList(), change.transitions)
    }

    @Test
    fun `A finding reported after its resolution is exposed again, since the new scan, and reopened`() {
        val change = compute(listOf(exposure(1).resolved()), reported(1))
        assertEquals(listOf(exposure(1, since = t1)), change.saved)
        assertEquals(listOf(new(1, reopened = true)), change.transitions)
    }

    @Test
    fun `The scan of one stamp never resolves the findings of another stamp`() {
        val change = compute(listOf(exposure(1, stamp = otherStamp)), *emptyArray())
        assertEquals(emptyList(), change.saved)
        assertEquals(emptyList(), change.transitions)
    }

    @Test
    fun `A finding reported by two stamps is new on the branch only once`() {
        val change = compute(listOf(exposure(1, stamp = otherStamp)), reported(1))
        assertEquals(listOf(exposure(1, since = t1)), change.saved)
        assertEquals(emptyList(), change.transitions)
    }

    @Test
    fun `A finding still reported by another stamp is not resolved on the branch`() {
        val change = compute(listOf(exposure(1), exposure(1, stamp = otherStamp)), *emptyArray())
        assertEquals(
            listOf(exposure(1).resolved()),
            change.saved
        )
        assertEquals(emptyList(), change.transitions)
    }

    @Test
    fun `A finding first seen accepted is exposed as accepted, and is not new`() {
        val change = compute(emptyList(), reported(1, accepted = true, expiresAt = LocalDate.of(2026, 12, 31)))
        assertEquals(
            listOf(exposure(1, since = t1, accepted = true, expiresAt = LocalDate.of(2026, 12, 31))),
            change.saved
        )
        assertEquals(FindingExposureState.ACCEPTED, change.saved.single().stateOn(t1.toLocalDate()))
        assertEquals(emptyList(), change.transitions)
    }

    @Test
    fun `A finding reported without acceptance after an expired one is new again`() {
        val expired = exposure(1, accepted = true, expiresAt = LocalDate.of(2026, 9, 21))
        val change = compute(listOf(expired), reported(1))
        assertEquals(listOf(exposure(1)), change.saved)
        assertEquals(listOf(new(1, reopened = true)), change.transitions)
    }

    @Test
    fun `A finding becoming accepted is neither new nor resolved`() {
        val change = compute(listOf(exposure(1)), reported(1, accepted = true))
        assertEquals(listOf(exposure(1, accepted = true)), change.saved)
        assertEquals(emptyList(), change.transitions)
    }

    @Test
    fun `An accepted finding no longer reported is resolved silently`() {
        val change = compute(listOf(exposure(1, accepted = true)), *emptyArray())
        assertEquals(listOf(exposure(1, accepted = true).resolved()), change.saved)
        assertEquals(emptyList(), change.transitions)
    }

    @Test
    fun `A finding whose acceptance has expired is exposed, and its resolution is signalled`() {
        val change = compute(
            listOf(exposure(1, accepted = true, expiresAt = LocalDate.of(2026, 9, 21))),
            *emptyArray()
        )
        assertEquals(listOf(resolved(1)), change.transitions)
    }

    @Test
    fun `The expiry of an acceptance is evaluated on the day it is read`() {
        val exposure = exposure(1, accepted = true, expiresAt = LocalDate.of(2026, 9, 21))
        assertEquals(FindingExposureState.ACCEPTED, exposure.stateOn(LocalDate.of(2026, 9, 21)))
        assertEquals(FindingExposureState.EXPOSED, exposure.stateOn(LocalDate.of(2026, 9, 22)))
        assertEquals(FindingExposureState.ACCEPTED, exposure.recordedState)
    }

    @Test
    fun `Rolling up states`() {
        assertEquals(null, FindingExposureState.of(emptyList()))
        assertEquals(
            FindingExposureState.EXPOSED,
            FindingExposureState.of(FindingExposureState.entries)
        )
        assertEquals(
            FindingExposureState.ACCEPTED,
            FindingExposureState.of(listOf(FindingExposureState.RESOLVED, FindingExposureState.ACCEPTED))
        )
        assertEquals(
            FindingExposureState.RESOLVED,
            FindingExposureState.of(listOf(FindingExposureState.RESOLVED, FindingExposureState.RESOLVED))
        )
    }

    private fun compute(branchExposures: List<FindingExposure>, vararg reported: ReportedExposure) =
        FindingsExposureComputation.compute(
            branchId = branch,
            validationStampId = stamp,
            branchExposures = branchExposures,
            reported = reported.toList(),
            time = t1,
        )

    private fun reported(findingId: Int, accepted: Boolean = false, expiresAt: LocalDate? = null) =
        ReportedExposure(findingId = findingId, accepted = accepted, acceptanceExpiresAt = expiresAt)

    private fun exposure(
        findingId: Int,
        stamp: Int = this.stamp,
        since: LocalDateTime = t0,
        accepted: Boolean = false,
        expiresAt: LocalDate? = null,
    ) = FindingExposure(
        findingId = findingId,
        branchId = branch,
        validationStampId = stamp,
        since = since,
        accepted = accepted,
        acceptanceExpiresAt = expiresAt,
    )

    private fun FindingExposure.resolved() =
        copy(resolvedAt = t1, resolutionReason = FindingResolutionReason.ABSENT)

    private fun new(findingId: Int, reopened: Boolean = false) =
        ExposureTransition(findingId, FindingExposureTransitionType.NEW, reopened)

    private fun resolved(findingId: Int) =
        ExposureTransition(findingId, FindingExposureTransitionType.RESOLVED)
}
