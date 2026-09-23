package net.nemerosa.ontrack.extension.findings.report

import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NeutralFindingsReportParserTest {

    private val parser = NeutralFindingsReportParser()

    @Test
    fun `The format is findings`() {
        assertEquals("findings", parser.format)
    }

    @Test
    fun `Parsing the example of the specification`() {
        val report = parser.parse(
            """
                {
                  "scanner": "zap",
                  "kind": "DAST",
                  "findings": [
                    {
                      "externalId": "10038",
                      "location": "",
                      "severity": "MEDIUM",
                      "rawSeverity": "Medium",
                      "title": "Content Security Policy (CSP) Header Not Set",
                      "url": "https://www.zaproxy.org/docs/alerts/10038/",
                      "fixedVersion": null,
                      "installedVersion": null,
                      "acceptance": {
                        "statement": "Served behind a proxy setting the header",
                        "expiresAt": "2026-12-31",
                        "source": "security/dast/suppressions.yaml"
                      }
                    }
                  ]
                }
            """.parseAsJson(),
            scanner = null,
            kind = null,
        )
        assertEquals("zap", report.scanner)
        assertEquals(FindingKind.DAST, report.kind)
        assertEquals(
            listOf(
                FindingsReportEntry(
                    externalId = "10038",
                    location = "",
                    severity = FindingSeverity.MEDIUM,
                    rawSeverity = "Medium",
                    title = "Content Security Policy (CSP) Header Not Set",
                    url = "https://www.zaproxy.org/docs/alerts/10038/",
                    fixedVersion = null,
                    installedVersion = null,
                    acceptance = FindingsReportAcceptance(
                        statement = "Served behind a proxy setting the header",
                        expiresAt = LocalDate.of(2026, 12, 31),
                        source = "security/dast/suppressions.yaml",
                    ),
                )
            ),
            report.findings
        )
    }

    @Test
    fun `Only the required fields`() {
        val report = parser.parse(
            """
                {
                  "scanner": "trivy",
                  "kind": "IMAGE",
                  "findings": [
                    {
                      "externalId": "CVE-2021-44228",
                      "location": "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1",
                      "severity": "CRITICAL",
                      "title": "Log4Shell"
                    }
                  ]
                }
            """.parseAsJson(),
            scanner = null,
            kind = null,
        )
        val entry = report.findings.single()
        assertEquals("CVE-2021-44228", entry.externalId)
        // The parser does not normalise the location, the ingestion does, for every format
        assertEquals("pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1", entry.location)
        assertEquals(FindingSeverity.CRITICAL, entry.severity)
        assertNull(entry.rawSeverity)
        assertNull(entry.acceptance)
    }

    @Test
    fun `An acceptance without expiry`() {
        val report = parser.parse(
            """
                {
                  "scanner": "codeql",
                  "kind": "CODE",
                  "findings": [
                    {
                      "externalId": "js/xss",
                      "location": "src/app.js",
                      "severity": "HIGH",
                      "title": "Cross-site scripting",
                      "acceptance": {
                        "statement": "False positive",
                        "source": "github"
                      }
                    }
                  ]
                }
            """.parseAsJson(),
            scanner = null,
            kind = null,
        )
        assertEquals(
            FindingsReportAcceptance(statement = "False positive", expiresAt = null, source = "github"),
            report.findings.single().acceptance
        )
    }

    @Test
    fun `An empty list of findings`() {
        val report = parser.parse(
            """{"scanner": "zap", "kind": "DAST", "findings": []}""".parseAsJson(),
            scanner = null,
            kind = null,
        )
        assertTrue(report.findings.isEmpty())
    }

    @Test
    fun `The scanner and the kind of the caller take precedence over the report's`() {
        val report = parser.parse(
            """{"scanner": "zap", "kind": "DAST", "findings": []}""".parseAsJson(),
            scanner = "zap-active",
            kind = FindingKind.OTHER,
        )
        assertEquals("zap-active", report.scanner)
        assertEquals(FindingKind.OTHER, report.kind)
    }

    @Test
    fun `The scanner and the kind may come from the caller only`() {
        val report = parser.parse(
            """{"findings": []}""".parseAsJson(),
            scanner = "gitleaks",
            kind = FindingKind.SECRETS,
        )
        assertEquals("gitleaks", report.scanner)
        assertEquals(FindingKind.SECRETS, report.kind)
    }

    @Test
    fun `A scanner is required`() {
        assertErrors(
            """{"kind": "DAST", "findings": []}""",
            "`scanner` is required, in the report or as an argument",
        )
    }

    @Test
    fun `A kind is required`() {
        assertErrors(
            """{"scanner": "zap", "findings": []}""",
            "`kind` is required, in the report or as an argument",
        )
    }

    @Test
    fun `Unknown field at the root is rejected`() {
        assertErrors(
            """{"scanner": "zap", "kind": "DAST", "findings": [], "target": "https://example.com"}""",
            "`target`: unknown field",
        )
    }

    @Test
    fun `Unknown field in a finding is rejected`() {
        assertErrors(
            """
                {
                  "scanner": "gitleaks",
                  "kind": "SECRETS",
                  "findings": [
                    {
                      "externalId": "aws-access-token",
                      "location": "42",
                      "severity": "HIGH",
                      "title": "AWS access token",
                      "secret": "AKIA..."
                    }
                  ]
                }
            """,
            "`findings[0].secret`: unknown field",
        )
    }

    @Test
    fun `Unknown field in an acceptance is rejected`() {
        assertErrors(
            """
                {
                  "scanner": "zap",
                  "kind": "DAST",
                  "findings": [
                    {
                      "externalId": "10038",
                      "location": "",
                      "severity": "MEDIUM",
                      "title": "CSP",
                      "acceptance": {"statement": "OK", "source": "file", "by": "me"}
                    }
                  ]
                }
            """,
            "`findings[0].acceptance.by`: unknown field",
        )
    }

    @Test
    fun `Missing required fields are rejected`() {
        assertErrors(
            """
                {
                  "scanner": "zap",
                  "kind": "DAST",
                  "findings": [
                    {
                      "rawSeverity": "Medium"
                    }
                  ]
                }
            """,
            "`findings[0].externalId`: required",
            "`findings[0].location`: required",
            "`findings[0].severity`: required",
            "`findings[0].title`: required",
        )
    }

    @Test
    fun `The findings are required`() {
        assertErrors(
            """{"scanner": "zap", "kind": "DAST"}""",
            "`findings`: required",
        )
    }

    @Test
    fun `A null location is rejected - it may be empty, never null`() {
        assertErrors(
            """
                {
                  "scanner": "zap",
                  "kind": "DAST",
                  "findings": [
                    {"externalId": "10038", "location": null, "severity": "MEDIUM", "title": "CSP"}
                  ]
                }
            """,
            "`findings[0].location`: required",
        )
    }

    @Test
    fun `A blank external ID or title is rejected`() {
        assertErrors(
            """
                {
                  "scanner": "zap",
                  "kind": "DAST",
                  "findings": [
                    {"externalId": " ", "location": "", "severity": "MEDIUM", "title": ""}
                  ]
                }
            """,
            "`findings[0].externalId`: must not be blank",
            "`findings[0].title`: must not be blank",
        )
    }

    @Test
    fun `Unknown severity and kind are rejected`() {
        assertErrors(
            """
                {
                  "scanner": "zap",
                  "kind": "WEB",
                  "findings": [
                    {"externalId": "10038", "location": "", "severity": "INFO", "title": "CSP"}
                  ]
                }
            """,
            "`kind`: must be one of IMAGE, CODE, SECRETS, DAST, DEPENDENCIES, OTHER",
            "`findings[0].severity`: must be one of CRITICAL, HIGH, MEDIUM, LOW, UNKNOWN",
        )
    }

    @Test
    fun `Wrong types are rejected`() {
        assertErrors(
            """
                {
                  "scanner": "zap",
                  "kind": "DAST",
                  "findings": [
                    {"externalId": 10038, "location": "", "severity": "MEDIUM", "title": "CSP", "url": ["x"]}
                  ]
                }
            """,
            "`findings[0].externalId`: must be a string",
            "`findings[0].url`: must be a string",
        )
    }

    @Test
    fun `A finding which is not an object is rejected`() {
        assertErrors(
            """{"scanner": "zap", "kind": "DAST", "findings": ["10038"]}""",
            "`findings[0]`: must be an object",
        )
    }

    @Test
    fun `A report which is not an object is rejected`() {
        assertErrors(
            """["10038"]""",
            "the report must be a JSON object",
        )
    }

    @Test
    fun `Invalid expiry date is rejected`() {
        assertErrors(
            """
                {
                  "scanner": "zap",
                  "kind": "DAST",
                  "findings": [
                    {
                      "externalId": "10038",
                      "location": "",
                      "severity": "MEDIUM",
                      "title": "CSP",
                      "acceptance": {"statement": "OK", "source": "file", "expiresAt": "31/12/2026"}
                    }
                  ]
                }
            """,
            "`findings[0].acceptance.expiresAt`: must be an ISO date (yyyy-MM-dd)",
        )
    }

    private fun assertErrors(report: String, vararg expectedErrors: String) {
        val ex = assertThrows<FindingsReportFormatException> {
            parser.parse(report.parseAsJson(), scanner = null, kind = null)
        }
        assertEquals(expectedErrors.toList(), ex.errors)
        assertTrue(ex.message!!.startsWith("Invalid findings report (format `findings`): "), ex.message)
    }
}
