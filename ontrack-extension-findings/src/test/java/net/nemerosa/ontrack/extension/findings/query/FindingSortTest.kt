package net.nemerosa.ontrack.extension.findings.query

import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingExposurePeriod
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class FindingSortTest {

    private fun t(day: Int) = LocalDateTime.of(2026, 10, day, 10, 0)

    private fun finding(id: Int, severity: FindingSeverity, lastSeenDay: Int = 20) = Finding(
        id = id,
        projectId = 1,
        scanner = "trivy",
        externalId = "CVE-$id",
        location = "",
        kind = FindingKind.DEPENDENCIES,
        title = "CVE-$id",
        url = null,
        firstSeen = t(1),
        lastSeen = t(lastSeenDay),
        resolvedAt = null,
        maxSeverity = severity,
    )

    private fun ongoingSince(day: Int) = FindingExposedFor.NONE.copy(
        ongoing = FindingExposurePeriod(
            id = day,
            findingId = 0,
            branchId = 10,
            validationStampId = 100,
            startedAt = t(day),
            startedByValidationRunId = null,
            startedInBuild = null,
        )
    )

    private val critical = finding(1, FindingSeverity.CRITICAL)
    private val high = finding(2, FindingSeverity.HIGH)
    private val medium = finding(3, FindingSeverity.MEDIUM)
    private val low = finding(4, FindingSeverity.LOW)
    private val resolvedCritical = finding(5, FindingSeverity.CRITICAL, lastSeenDay = 10)
    private val resolvedLow = finding(6, FindingSeverity.LOW, lastSeenDay = 10)
    private val findings = listOf(low, resolvedLow, medium, critical, resolvedCritical, high)

    @Test
    fun `Default order, the most severe first, then the most recently seen`() {
        assertEquals(
            listOf(critical, resolvedCritical, high, medium, low, resolvedLow),
            findings.sortedWith(FindingSort.DEFAULT.comparator())
        )
    }

    @Test
    fun `The longest exposed first, ties in the default order, the findings without ongoing period last`() {
        val exposedFor = mapOf(
            critical.id to ongoingSince(8),
            high.id to ongoingSince(3),
            medium.id to ongoingSince(8),
            low.id to ongoingSince(2),
            // Fixed
            resolvedLow.id to FindingExposedFor.NONE,
        )
        assertEquals(
            listOf(low, high, critical, medium, resolvedCritical, resolvedLow),
            findings.sortedWith(FindingSort.EXPOSED_FOR.comparator(exposedFor))
        )
    }
}
