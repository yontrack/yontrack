package net.nemerosa.ontrack.extension.findings.ingestion

import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.report.FindingsReportAcceptance
import net.nemerosa.ontrack.extension.findings.report.FindingsReportEntry
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataTypeData
import net.nemerosa.ontrack.extension.general.validation.CHML
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FindingsConsolidationTest {

    private val today = LocalDate.of(2026, 9, 23)

    @Test
    fun `A purl location is stored without version, which goes to the installed version`() {
        val finding = FindingsConsolidation.consolidate(
            listOf(entry(location = "pkg:maven/org.x/y@1.2.3?type=jar")),
            today
        ).single()
        assertEquals("pkg:maven/org.x/y", finding.location)
        assertEquals("1.2.3", finding.installedVersion)
    }

    @Test
    fun `An explicit installed version wins over the version of the purl`() {
        val finding = FindingsConsolidation.consolidate(
            listOf(entry(location = "pkg:maven/org.x/y@1.2.3", installedVersion = "1.2.3-patched")),
            today
        ).single()
        assertEquals("1.2.3-patched", finding.installedVersion)
    }

    @Test
    fun `One CVE on two versions of a package is one finding, the most severe entry kept`() {
        val findings = FindingsConsolidation.consolidate(
            listOf(
                entry(externalId = "CVE-1", location = "pkg:maven/org.x/y@1.0", severity = FindingSeverity.MEDIUM),
                entry(externalId = "CVE-1", location = "pkg:maven/org.x/y@2.0", severity = FindingSeverity.HIGH),
                entry(externalId = "CVE-2", location = "pkg:maven/org.x/y@2.0", severity = FindingSeverity.LOW),
            ),
            today
        )
        assertEquals(listOf("CVE-1", "CVE-2"), findings.map { it.externalId })
        assertEquals(FindingSeverity.HIGH, findings[0].severity)
        assertEquals("2.0", findings[0].installedVersion)
    }

    @Test
    fun `One CVE in two different packages is two findings`() {
        val findings = FindingsConsolidation.consolidate(
            listOf(
                entry(externalId = "CVE-1", location = "pkg:deb/debian/openssl@3.0"),
                entry(externalId = "CVE-1", location = "pkg:deb/debian/libssl3@3.0"),
            ),
            today
        )
        assertEquals(listOf("pkg:deb/debian/openssl", "pkg:deb/debian/libssl3"), findings.map { it.location })
    }

    @Test
    fun `Among duplicates, a not accepted entry wins over an accepted one`() {
        val finding = FindingsConsolidation.consolidate(
            listOf(
                entry(externalId = "CVE-1", severity = FindingSeverity.CRITICAL, acceptance = acceptance()),
                entry(externalId = "CVE-1", severity = FindingSeverity.LOW),
            ),
            today
        ).single()
        assertFalse(finding.accepted)
        assertEquals(FindingSeverity.LOW, finding.severity)
    }

    @Test
    fun `An acceptance holds until its expiry day included`() {
        fun accepted(expiresAt: LocalDate?) = FindingsConsolidation.consolidate(
            listOf(entry(acceptance = acceptance(expiresAt))),
            today
        ).single().accepted
        assertTrue(accepted(null))
        assertTrue(accepted(today))
        assertTrue(accepted(today.plusDays(1)))
        assertFalse(accepted(today.minusDays(1)))
    }

    @Test
    fun `An expired acceptance is kept on the finding, for the record`() {
        val finding = FindingsConsolidation.consolidate(
            listOf(entry(acceptance = acceptance(today.minusDays(1)))),
            today
        ).single()
        assertFalse(finding.accepted)
        assertEquals(today.minusDays(1), finding.acceptance?.expiresAt)
    }

    @Test
    fun `Counts per severity, UNKNOWN apart, accepted apart`() {
        val findings = FindingsConsolidation.consolidate(
            listOf(
                entry(externalId = "1", severity = FindingSeverity.CRITICAL),
                entry(externalId = "2", severity = FindingSeverity.HIGH),
                entry(externalId = "3", severity = FindingSeverity.HIGH),
                entry(externalId = "4", severity = FindingSeverity.LOW),
                entry(externalId = "5", severity = FindingSeverity.UNKNOWN),
                entry(externalId = "6", severity = FindingSeverity.CRITICAL, acceptance = acceptance()),
                entry(externalId = "7", severity = FindingSeverity.UNKNOWN, acceptance = acceptance()),
                entry(externalId = "8", severity = FindingSeverity.HIGH, acceptance = acceptance(today.minusDays(1))),
            ),
            today
        )
        assertEquals(
            FindingsValidationDataTypeData(
                levels = mapOf(
                    CHML.CRITICAL to 1,
                    CHML.HIGH to 3,
                    CHML.MEDIUM to 0,
                    CHML.LOW to 1,
                ),
                unknown = 1,
                accepted = 2,
            ),
            FindingsConsolidation.counts(findings)
        )
    }

    @Test
    fun `No finding, all counts at zero`() {
        assertEquals(
            FindingsValidationDataTypeData(
                levels = CHML.entries.associateWith { 0 },
                unknown = 0,
                accepted = 0,
            ),
            FindingsConsolidation.counts(emptyList())
        )
    }

    private fun entry(
        externalId: String = "CVE-2021-44228",
        location: String = "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1",
        severity: FindingSeverity = FindingSeverity.CRITICAL,
        installedVersion: String? = null,
        acceptance: FindingsReportAcceptance? = null,
    ) = FindingsReportEntry(
        externalId = externalId,
        location = location,
        severity = severity,
        title = "Title of $externalId",
        installedVersion = installedVersion,
        acceptance = acceptance,
    )

    private fun acceptance(expiresAt: LocalDate? = null) = FindingsReportAcceptance(
        statement = "Not reachable",
        expiresAt = expiresAt,
        source = ".trivyignore.yaml",
    )
}
