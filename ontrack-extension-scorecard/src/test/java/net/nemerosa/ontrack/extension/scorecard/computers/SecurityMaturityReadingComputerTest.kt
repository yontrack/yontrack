package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.samples.SecurityCoverage
import net.nemerosa.ontrack.extension.scorecard.samples.SecurityRunSample
import net.nemerosa.ontrack.extension.scorecard.samples.SecurityStamp
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `security.maturity`, climbed rung by rung on plain lists of scans.
 */
class SecurityMaturityReadingComputerTest {

    private val end = LocalDateTime.of(2026, 10, 1, 2, 0)
    private val window = Interval(end.minusDays(90), end)

    private val scan = SecurityStamp(id = 10, branchId = 1, name = "scan")

    /**
     * No estate: no expected kind, any scan fresher than 7 days
     */
    private val noEstate = SecurityCoverage(expectedKinds = emptySet(), freshnessDays = 7)

    /**
     * An estate expecting image and code scans fresher than 14 days
     */
    private val imageAndCode = SecurityCoverage(
        expectedKinds = setOf(FindingKind.IMAGE, FindingKind.CODE),
        freshnessDays = 14,
    )

    private var nextRunId = 1

    private fun run(
        daysAgo: Long,
        vararg kinds: FindingKind,
        status: String = ValidationRunStatusID.PASSED,
    ) = SecurityRunSample(
        branchId = scan.branchId,
        stampId = scan.id,
        runId = nextRunId++,
        status = status,
        time = end.minusDays(daysAgo),
        kinds = kinds.toSet(),
    )

    private fun rung(
        coverage: SecurityCoverage,
        runs: List<SecurityRunSample>,
        requiredStamps: List<SecurityStamp> = emptyList(),
    ): Double? {
        val outcome = SecurityMaturityReadingComputer.aggregate(window, coverage, runs, requiredStamps)
        assertEquals(ReadingBasis.MEASURED, outcome.basis, "The maturity is always measured")
        assertNull(outcome.unknownReason)
        return outcome.value
    }

    // Rung 0

    @Test
    fun `Rung 0 with no scan at all`() {
        assertEquals(0.0, rung(noEstate, emptyList()))
    }

    @Test
    fun `Rung 0 with scans before the window only`() {
        assertEquals(0.0, rung(noEstate, listOf(run(120, FindingKind.IMAGE))))
    }

    @Test
    fun `Rung 0 even when a security stamp is required by a promotion, without any scan in the window`() {
        assertEquals(0.0, rung(noEstate, emptyList(), requiredStamps = listOf(scan)))
    }

    // Rung 1

    @Test
    fun `Rung 1 with a scan in the window which is not fresh`() {
        assertEquals(1.0, rung(noEstate, listOf(run(30, FindingKind.IMAGE))))
    }

    @Test
    fun `Rung 1 when an expected kind has no fresh scan`() {
        assertEquals(
            1.0,
            rung(
                imageAndCode,
                listOf(
                    run(2, FindingKind.IMAGE),
                    // Code scanned, but too long ago
                    run(20, FindingKind.CODE),
                )
            )
        )
    }

    @Test
    fun `Rung 1 when a scan fails, but coverage is missing`() {
        assertEquals(
            1.0,
            rung(imageAndCode, listOf(run(2, FindingKind.IMAGE, status = ValidationRunStatusID.FAILED)))
        )
    }

    // Rung 2

    @Test
    fun `Rung 2 with no estate when some scan is fresher than the global freshness`() {
        assertEquals(2.0, rung(noEstate, listOf(run(30, FindingKind.CODE), run(3, FindingKind.OTHER))))
    }

    @Test
    fun `Rung 2 with a clean scan, which reports no finding but has its kind`() {
        assertEquals(2.0, rung(noEstate, listOf(run(1, FindingKind.SECRETS))))
    }

    @Test
    fun `Rung 2 when every expected kind has a fresh scan`() {
        assertEquals(
            2.0,
            rung(
                imageAndCode,
                listOf(
                    run(13, FindingKind.IMAGE),
                    run(5, FindingKind.CODE),
                    run(60, FindingKind.DAST),
                )
            )
        )
    }

    @Test
    fun `A scan of no known kind counts as reported and, with no expected kind, as covered`() {
        assertEquals(1.0, rung(imageAndCode, listOf(run(1))))
        assertEquals(2.0, rung(noEstate, listOf(run(1))))
    }

    // Rung 3

    @Test
    fun `Rung 3 when a security stamp is required by a promotion level`() {
        assertEquals(2.0, rung(noEstate, listOf(run(1, FindingKind.IMAGE))))
        assertEquals(3.0, rung(noEstate, listOf(run(1, FindingKind.IMAGE)), requiredStamps = listOf(scan)))
    }

    @Test
    fun `Rung 3 when a scan was created FAILED in the window`() {
        assertEquals(
            3.0,
            rung(
                imageAndCode,
                listOf(
                    run(40, FindingKind.IMAGE, status = ValidationRunStatusID.FAILED),
                    run(2, FindingKind.IMAGE),
                    run(3, FindingKind.CODE),
                )
            )
        )
    }

    @Test
    fun `A scan failed before the window does not make it gating`() {
        assertEquals(
            2.0,
            rung(
                noEstate,
                listOf(
                    run(100, FindingKind.IMAGE, status = ValidationRunStatusID.FAILED),
                    run(2, FindingKind.IMAGE),
                )
            )
        )
    }

    // Details

    @Test
    fun `Details say what each rung rests on`() {
        val outcome = SecurityMaturityReadingComputer.aggregate(
            window,
            imageAndCode,
            listOf(
                run(100, FindingKind.CODE),
                run(30, FindingKind.CODE, status = ValidationRunStatusID.FAILED),
                run(2, FindingKind.IMAGE),
                run(1, FindingKind.IMAGE),
            ),
            listOf(scan),
        )
        assertEquals(1.0, outcome.value)
        assertEquals(3, outcome.details["count"])
        assertEquals(true, outcome.details["reported"])
        assertEquals(false, outcome.details["covered"])
        assertEquals(true, outcome.details["gating"])
        assertEquals(listOf("IMAGE", "CODE"), outcome.details["expectedKinds"], "In the order of the kinds")
        assertEquals(14, outcome.details["freshnessDays"])
        assertEquals(listOf("IMAGE"), outcome.details["freshKinds"])
        assertEquals(listOf("CODE"), outcome.details["missingKinds"])
        assertEquals(1, outcome.details["failedScans"])
        assertEquals(listOf("scan"), outcome.details["requiredStamps"])
        assertEquals(end.minusDays(1), outcome.details["lastScan"])
    }

    @Test
    fun `Details with no scan`() {
        val outcome = SecurityMaturityReadingComputer.aggregate(window, noEstate, emptyList(), emptyList())
        assertEquals(0.0, outcome.value)
        assertEquals(0, outcome.details["count"])
        assertEquals(false, outcome.details["reported"])
        assertEquals(false, outcome.details["covered"])
        assertEquals(false, outcome.details["gating"])
        assertEquals(emptyList<String>(), outcome.details["expectedKinds"])
        assertEquals(7, outcome.details["freshnessDays"])
        assertEquals(emptyList<String>(), outcome.details["freshKinds"])
        assertEquals(emptyList<String>(), outcome.details["missingKinds"])
        assertNull(outcome.details["lastScan"])
    }
}
