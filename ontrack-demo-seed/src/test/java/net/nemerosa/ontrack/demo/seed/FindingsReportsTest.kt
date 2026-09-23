package net.nemerosa.ontrack.demo.seed

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FindingsReportsTest {

    private val today = LocalDate.of(2026, 9, 1)

    private val cve = FindingSpec(
        externalId = "CVE-2024-38816",
        location = "pkg:maven/org.springframework/spring-webmvc",
        severity = FindingSeverity.HIGH,
        title = "Path traversal",
        url = "https://nvd.nist.gov/vuln/detail/CVE-2024-38816",
        installedVersion = "6.1.12",
        fixedVersion = "6.1.13",
    )

    private val accepted = FindingSpec(
        externalId = "CVE-2022-1471",
        location = "pkg:maven/org.yaml/snakeyaml",
        severity = FindingSeverity.CRITICAL,
        title = "Constructor deserialization",
        acceptance = AcceptanceSpec("Not reachable", ".trivyignore.yaml", expiresInDays = 90),
    )

    private fun scan(format: ScanFormat, vararg findings: FindingSpec) = ScanSpec(
        validationStamp = "SCAN",
        format = format,
        kind = ScanKind.DEPENDENCIES,
        scanner = "trivy",
        findings = findings.toList(),
    )

    @Test
    fun `the neutral format carries the findings, and the expiry of an acceptance counted from the reset`() {
        val report = FindingsReports.render(scan(ScanFormat.FINDINGS, cve, accepted), today)

        assertEquals("trivy", report.path("scanner").asString())
        assertEquals("DEPENDENCIES", report.path("kind").asString())
        val (high, critical) = report.path("findings").values().toList()
        assertEquals("CVE-2024-38816", high.path("externalId").asString())
        assertEquals("pkg:maven/org.springframework/spring-webmvc", high.path("location").asString())
        assertEquals("HIGH", high.path("severity").asString())
        assertEquals("Path traversal", high.path("title").asString())
        assertEquals("6.1.12", high.path("installedVersion").asString())
        assertEquals("6.1.13", high.path("fixedVersion").asString())
        assertFalse(high.has("acceptance"), "No acceptance, no field")

        assertFalse(critical.has("url"), "An absent optional field is left out, not null")
        val acceptance = critical.path("acceptance")
        assertEquals("Not reachable", acceptance.path("statement").asString())
        assertEquals(".trivyignore.yaml", acceptance.path("source").asString())
        assertEquals("2026-11-30", acceptance.path("expiresAt").asString())
    }

    @Test
    fun `an acceptance without expiry has no expiry in the neutral format`() {
        val report = FindingsReports.render(
            scan(ScanFormat.FINDINGS, accepted.copy(acceptance = AcceptanceSpec("Not reachable", "file"))),
            today,
        )
        assertFalse(report.path("findings").path(0).path("acceptance").has("expiresAt"))
    }

    @Test
    fun `SARIF carries the scanner as its tool, the severity as a security severity, and the acceptance as a suppression`() {
        val report = FindingsReports.render(
            scan(
                ScanFormat.SARIF,
                cve,
                accepted.copy(acceptance = AcceptanceSpec("Not reachable", ".github/codeql/suppressions.sarif")),
            ),
            today,
        )

        assertEquals("2.1.0", report.path("version").asString())
        val run = report.path("runs").values().single()
        assertEquals("trivy", run.path("tool").path("driver").path("name").asString())

        val rules = run.path("tool").path("driver").path("rules").values().toList()
        assertEquals(listOf("CVE-2024-38816", "CVE-2022-1471"), rules.map { it.path("id").asString() })
        assertEquals("Path traversal", rules[0].path("shortDescription").path("text").asString())
        assertEquals("https://nvd.nist.gov/vuln/detail/CVE-2024-38816", rules[0].path("helpUri").asString())
        assertEquals("7.5", rules[0].path("properties").path("security-severity").asString())
        assertEquals("9.8", rules[1].path("properties").path("security-severity").asString())

        val results = run.path("results").values().toList()
        assertEquals(
            "pkg:maven/org.springframework/spring-webmvc",
            results[0].path("locations").path(0).path("physicalLocation").path("artifactLocation").path("uri").asString(),
        )
        assertFalse(results[0].has("suppressions"))
        val suppression = results[1].path("suppressions").values().single()
        assertEquals("accepted", suppression.path("status").asString())
        assertEquals("Not reachable", suppression.path("justification").asString())
        assertEquals(
            ".github/codeql/suppressions.sarif",
            suppression.path("location").path("physicalLocation").path("artifactLocation").path("uri").asString(),
        )
    }

    @Test
    fun `SARIF gives UNKNOWN no security severity`() {
        val report = FindingsReports.render(scan(ScanFormat.SARIF, cve.copy(severity = FindingSeverity.UNKNOWN)), today)
        val rule = report.path("runs").path(0).path("tool").path("driver").path("rules").path(0)
        assertTrue(rule.path("properties").isMissingNode)
    }

    @Test
    fun `SARIF declares a rule once, however many results it has`() {
        val report = FindingsReports.render(
            scan(ScanFormat.SARIF, cve, cve.copy(location = "another/place")),
            today,
        )
        val run = report.path("runs").path(0)
        assertEquals(1, run.path("tool").path("driver").path("rules").size())
        assertEquals(2, run.path("results").size())
    }
}
