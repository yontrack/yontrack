package net.nemerosa.ontrack.extension.findings.report

import net.nemerosa.ontrack.extension.findings.ingestion.FindingsConsolidation
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SarifFindingsReportParserTest {

    private val parser = SarifFindingsReportParser()

    private fun sample(name: String): JsonNode = TestUtils.resourceJson("/sarif/$name.sarif")

    @Test
    fun `The format is sarif, a native format`() {
        assertEquals("sarif", parser.format)
        assertTrue(parser.nativeFormat)
    }

    @Test
    fun `The neutral format is not a native format`() {
        assertFalse(NeutralFindingsReportParser().nativeFormat)
    }

    // ---------------------------------------------------------------------------------------------
    // Samples
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `CodeQL sample`() {
        val report = parser.parse(sample("codeql"), scanner = null, kind = FindingKind.CODE)
        assertEquals("codeql", report.scanner)
        assertEquals(FindingKind.CODE, report.kind)
        assertEquals(
            listOf(
                FindingsReportEntry(
                    externalId = "java/sql-injection",
                    location = "src/main/java/com/example/OrderDao.java",
                    severity = FindingSeverity.HIGH,
                    rawSeverity = "security-severity=8.8",
                    title = "Query built from user-controlled sources",
                    url = "https://codeql.github.com/codeql-query-help/java/java-sql-injection/",
                ),
                FindingsReportEntry(
                    externalId = "java/sql-injection",
                    location = "src/main/java/com/example/OrderDao.java",
                    severity = FindingSeverity.HIGH,
                    rawSeverity = "security-severity=8.8",
                    title = "Query built from user-controlled sources",
                    url = "https://codeql.github.com/codeql-query-help/java/java-sql-injection/",
                ),
                FindingsReportEntry(
                    externalId = "java/log-injection",
                    location = "src/main/java/com/example/OrderController.java",
                    severity = FindingSeverity.HIGH,
                    rawSeverity = "security-severity=7.8",
                    title = "Log Injection",
                ),
                FindingsReportEntry(
                    externalId = "java/insecure-cookie",
                    location = "src/main/java/com/example/OrderController.java",
                    severity = FindingSeverity.MEDIUM,
                    rawSeverity = "security-severity=5.0",
                    title = "Failure to use secure cookies",
                    acceptance = FindingsReportAcceptance(
                        statement = "",
                        expiresAt = null,
                        source = "SARIF suppression (inSource)",
                    ),
                ),
                // Location through the artifacts of the run, severity from the default level of the rule
                FindingsReportEntry(
                    externalId = "java/unused-parameter",
                    location = "src/main/java/com/example/OrderController.java",
                    severity = FindingSeverity.LOW,
                    rawSeverity = "level=note",
                    title = "Useless parameter",
                ),
            ),
            report.findings
        )
    }

    @Test
    fun `CodeQL sample - several results of one rule in one file are one finding`() {
        val report = parser.parse(sample("codeql"), scanner = null, kind = FindingKind.CODE)
        val findings = FindingsConsolidation.consolidate(report.findings, LocalDate.of(2026, 9, 23))
        assertEquals(4, findings.size)
        assertEquals(1, findings.count { it.externalId == "java/sql-injection" })
        // The suppressed result is accepted
        assertTrue(findings.single { it.externalId == "java/insecure-cookie" }.accepted)
    }

    @Test
    fun `Trivy sample`() {
        val report = parser.parse(sample("trivy"), scanner = null, kind = FindingKind.IMAGE)
        assertEquals("trivy", report.scanner)
        assertEquals(FindingKind.IMAGE, report.kind)
        assertEquals(
            listOf(
                FindingsReportEntry(
                    externalId = "CVE-2023-5363",
                    location = "library/debian",
                    severity = FindingSeverity.HIGH,
                    rawSeverity = "security-severity=7.5",
                    title = "openssl: Incorrect cipher key and IV length processing",
                    url = "https://avd.aquasec.com/nvd/cve-2023-5363",
                ),
                FindingsReportEntry(
                    externalId = "CVE-2023-5363",
                    location = "library/debian",
                    severity = FindingSeverity.HIGH,
                    rawSeverity = "security-severity=7.5",
                    title = "openssl: Incorrect cipher key and IV length processing",
                    url = "https://avd.aquasec.com/nvd/cve-2023-5363",
                ),
                FindingsReportEntry(
                    externalId = "CVE-2021-44228",
                    location = "app/lib/log4j-core-2.14.1.jar",
                    severity = FindingSeverity.CRITICAL,
                    rawSeverity = "security-severity=10.0",
                    title = "log4j-core: Remote code execution in Log4j 2.x when logs contain an attacker-controlled string value",
                    url = "https://avd.aquasec.com/nvd/cve-2021-44228",
                ),
                FindingsReportEntry(
                    externalId = "CVE-2023-4039",
                    location = "library/debian",
                    severity = FindingSeverity.MEDIUM,
                    rawSeverity = "security-severity=4.8",
                    title = "gcc: -fstack-protector fails to guard dynamic stack allocations on ARM64",
                    url = "https://avd.aquasec.com/nvd/cve-2023-4039",
                ),
                FindingsReportEntry(
                    externalId = "CVE-2011-3374",
                    location = "library/debian",
                    severity = FindingSeverity.LOW,
                    rawSeverity = "security-severity=3.7",
                    title = "It was found that apt-key in apt, all versions, do not correctly validate gpg keys with the master keyring, leading to a potential man-in-the-middle attack.",
                    url = "https://avd.aquasec.com/nvd/cve-2011-3374",
                ),
                FindingsReportEntry(
                    externalId = "aws-access-key-id",
                    location = "config/deploy.env",
                    severity = FindingSeverity.CRITICAL,
                    rawSeverity = "security-severity=9.5",
                    title = "AWS Access Key ID",
                    url = "https://github.com/aquasecurity/trivy/blob/main/pkg/fanal/secret/builtin-rules.go",
                ),
            ),
            report.findings
        )
    }

    @Test
    fun `Semgrep sample`() {
        val report = parser.parse(sample("semgrep"), scanner = null, kind = FindingKind.CODE)
        assertEquals("semgrep oss", report.scanner)
        assertEquals(
            listOf(
                FindingsReportEntry(
                    externalId = "python.django.security.injection.sql.sql-injection-using-db-cursor-execute.sql-injection-db-cursor-execute",
                    location = "app/views.py",
                    severity = FindingSeverity.HIGH,
                    rawSeverity = "level=error",
                    title = "Semgrep Finding: python.django.security.injection.sql.sql-injection-using-db-cursor-execute.sql-injection-db-cursor-execute",
                    url = "https://semgrep.dev/r/python.django.security.injection.sql.sql-injection-using-db-cursor-execute.sql-injection-db-cursor-execute",
                ),
                FindingsReportEntry(
                    externalId = "generic.secrets.security.detected-aws-access-key-id-value.detected-aws-access-key-id-value",
                    location = "app/settings.py",
                    severity = FindingSeverity.MEDIUM,
                    rawSeverity = "level=warning",
                    title = "Semgrep Finding: generic.secrets.security.detected-aws-access-key-id-value.detected-aws-access-key-id-value",
                    url = "https://semgrep.dev/r/generic.secrets.security.detected-aws-access-key-id-value.detected-aws-access-key-id-value",
                ),
                FindingsReportEntry(
                    externalId = "python.flask.security.xss.audit.template-unescaped-with-safe.template-unescaped-with-safe",
                    location = "app/templates/order.html",
                    severity = FindingSeverity.MEDIUM,
                    rawSeverity = "level=warning",
                    title = "Semgrep Finding: python.flask.security.xss.audit.template-unescaped-with-safe.template-unescaped-with-safe",
                    url = "https://semgrep.dev/r/python.flask.security.xss.audit.template-unescaped-with-safe.template-unescaped-with-safe",
                    acceptance = FindingsReportAcceptance(
                        statement = "",
                        expiresAt = null,
                        source = "SARIF suppression (inSource)",
                    ),
                ),
            ),
            report.findings
        )
    }

    @Test
    fun `Messages, snippets and context regions are never read`() {
        listOf("trivy" to FindingKind.SECRETS, "semgrep" to FindingKind.CODE, "codeql" to FindingKind.CODE)
            .forEach { (name, kind) ->
                val text = parser.parse(sample(name), scanner = null, kind = kind).asJson().toString()
                listOf(
                    "AKIAIOSFODNN7EXAMPLE",
                    "wJalrXUtnFEMI",
                    "cursor.execute",
                    "user-provided value",
                    "Match:",
                    "is never used",
                ).forEach { secret ->
                    assertFalse(secret in text, "`$secret` must not be read from the $name sample")
                }
            }
    }

    // ---------------------------------------------------------------------------------------------
    // Scanner and kind
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `The scanner given by the caller takes precedence over the tool name`() {
        val report = parser.parse(sample("semgrep"), scanner = "semgrep", kind = FindingKind.CODE)
        assertEquals("semgrep", report.scanner)
    }

    @Test
    fun `The kind is required from the caller`() {
        val ex = assertThrows<FindingsReportFormatException> {
            parser.parse(sample("semgrep"), scanner = null, kind = null)
        }
        assertEquals(
            "Invalid findings report (format `sarif`): `kind` is required as an argument, a SARIF report cannot give it",
            ex.message
        )
    }

    @Test
    fun `The kind applies to every run and the findings of every run are read`() {
        val report = parser.parse(
            sarif(
                run(result("A"), toolName = "CodeQL"),
                run(result("B"), toolName = "CodeQL"),
            ),
            scanner = null,
            kind = FindingKind.CODE,
        )
        assertEquals("codeql", report.scanner)
        assertEquals(FindingKind.CODE, report.kind)
        assertEquals(listOf("A", "B"), report.findings.map { it.externalId })
    }

    @Test
    fun `Runs of different tools need the scanner from the caller`() {
        val report = sarif(
            run(result("A"), toolName = "CodeQL"),
            run(result("B"), toolName = "Semgrep OSS"),
        )
        val ex = assertThrows<FindingsReportFormatException> {
            parser.parse(report, scanner = null, kind = FindingKind.CODE)
        }
        assertEquals(
            "Invalid findings report (format `sarif`): the runs come from different tools: `scanner` is required as an argument",
            ex.message
        )
        assertEquals("sast", parser.parse(report, scanner = "sast", kind = FindingKind.CODE).scanner)
    }

    @Test
    fun `A report without any run is a scan without findings when the scanner is given`() {
        val report = parser.parse("""{"version": "2.1.0", "runs": []}""".parseAsJson(), "codeql", FindingKind.CODE)
        assertEquals("codeql", report.scanner)
        assertTrue(report.findings.isEmpty())
    }

    @Test
    fun `A report without any run needs the scanner from the caller`() {
        val ex = assertThrows<FindingsReportFormatException> {
            parser.parse("""{"version": "2.1.0", "runs": []}""".parseAsJson(), null, FindingKind.CODE)
        }
        assertEquals(
            "Invalid findings report (format `sarif`): `scanner` is required as an argument when the report has no run",
            ex.message
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Rule, location, title, URL
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `The location is the first location without its region, empty when there is none`() {
        val report = parser.parse(
            sarif(
                run(
                    result(
                        "A",
                        locations = """[
                            {"physicalLocation": {"artifactLocation": {"uri": "src/a.js"}, "region": {"startLine": 3, "startColumn": 7}}},
                            {"physicalLocation": {"artifactLocation": {"uri": "src/b.js"}}}
                        ]"""
                    ),
                    result("B", locations = null),
                    result("C", locations = "[]"),
                    result("D", locations = """[{"logicalLocations": [{"fullyQualifiedName": "a.b.C"}]}]"""),
                )
            ),
            null,
            FindingKind.CODE,
        )
        assertEquals(listOf("src/a.js", "", "", ""), report.findings.map { it.location })
    }

    @Test
    fun `The rule is found by its index, by its reference or by its id`() {
        val report = parser.parse(
            """
                {
                  "version": "2.1.0",
                  "runs": [{
                    "tool": {
                      "driver": {
                        "name": "Tool",
                        "rules": [
                          {"id": "R1", "shortDescription": {"text": "Rule one"}},
                          {"id": "R2", "name": "RuleTwo"}
                        ]
                      },
                      "extensions": [{
                        "name": "pack",
                        "rules": [
                          {"id": "X1", "shortDescription": {"text": "Extension rule"}, "helpUri": "https://example.com/x1"}
                        ]
                      }]
                    },
                    "results": [
                      {"ruleId": "R1", "ruleIndex": 0},
                      {"ruleId": "R2"},
                      {"rule": {"id": "X1", "index": 0, "toolComponent": {"index": 0}}},
                      {"ruleIndex": 1},
                      {"ruleId": "R1/2"},
                      {"ruleId": "UNKNOWN-RULE"}
                    ]
                  }]
                }
            """.parseAsJson(),
            null,
            FindingKind.CODE,
        )
        assertEquals(
            listOf(
                Triple("R1", "Rule one", null),
                Triple("R2", "RuleTwo", null),
                Triple("X1", "Extension rule", "https://example.com/x1"),
                Triple("R2", "RuleTwo", null),
                Triple("R1/2", "Rule one", null),
                Triple("UNKNOWN-RULE", "UNKNOWN-RULE", null),
            ),
            report.findings.map { Triple(it.externalId, it.title, it.url) }
        )
    }

    @Test
    fun `A result without any rule id is rejected`() {
        val ex = assertThrows<FindingsReportFormatException> {
            parser.parse(sarif(run(result(null))), null, FindingKind.CODE)
        }
        assertEquals(
            "Invalid findings report (format `sarif`): `runs[0].results[0]`: no rule id",
            ex.message
        )
    }

    @Test
    fun `A report which is not SARIF 2_1 is rejected`() {
        assertEquals(
            "Invalid findings report (format `sarif`): the report must be a JSON object",
            assertThrows<FindingsReportFormatException> {
                parser.parse("[]".parseAsJson(), null, FindingKind.CODE)
            }.message
        )
        assertEquals(
            "Invalid findings report (format `sarif`): `runs`: required, as an array",
            assertThrows<FindingsReportFormatException> {
                parser.parse("""{"version": "2.1.0"}""".parseAsJson(), null, FindingKind.CODE)
            }.message
        )
        assertEquals(
            "Invalid findings report (format `sarif`): `version`: only SARIF 2.1 is supported",
            assertThrows<FindingsReportFormatException> {
                parser.parse("""{"version": "1.0.0", "runs": []}""".parseAsJson(), "x", FindingKind.CODE)
            }.message
        )
    }

    @Test
    fun `Results which are not failures are not findings`() {
        val report = parser.parse(
            sarif(
                run(
                    result("PASS", extra = """"kind": "pass""""),
                    result("NA", extra = """"kind": "notApplicable""""),
                    result("GONE", extra = """"baselineState": "absent""""),
                    result("FAIL", extra = """"kind": "fail""""),
                    result("OPEN", extra = """"kind": "open""""),
                    result("NEW", extra = """"baselineState": "new""""),
                )
            ),
            null,
            FindingKind.CODE,
        )
        assertEquals(listOf("FAIL", "OPEN", "NEW"), report.findings.map { it.externalId })
    }

    // ---------------------------------------------------------------------------------------------
    // Severity
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `Severity from the security severity, by the thresholds of GitHub`() {
        val cases = listOf(
            "10.0" to FindingSeverity.CRITICAL,
            "9.0" to FindingSeverity.CRITICAL,
            "8.9" to FindingSeverity.HIGH,
            "7.0" to FindingSeverity.HIGH,
            "6.9" to FindingSeverity.MEDIUM,
            "4.0" to FindingSeverity.MEDIUM,
            "3.9" to FindingSeverity.LOW,
            "0.1" to FindingSeverity.LOW,
        )
        cases.forEach { (score, expected) ->
            val entry = parser.parse(
                sarif(run(result("A", extra = """"level": "note", "properties": {"security-severity": "$score"}"""))),
                null,
                FindingKind.CODE,
            ).findings.single()
            assertEquals(expected, entry.severity, "security-severity=$score")
            assertEquals("security-severity=$score", entry.rawSeverity)
        }
    }

    @Test
    fun `Security severity as a number`() {
        val entry = parser.parse(
            sarif(run(result("A", extra = """"properties": {"security-severity": 9.8}"""))),
            null,
            FindingKind.CODE,
        ).findings.single()
        assertEquals(FindingSeverity.CRITICAL, entry.severity)
        assertEquals("security-severity=9.8", entry.rawSeverity)
    }

    @Test
    fun `Security severity of the result before the one of the rule`() {
        val entry = parser.parse(
            sarif(
                run(
                    result("A", extra = """"properties": {"security-severity": "9.1"}"""),
                    rules = """[{"id": "A", "properties": {"security-severity": "5.0"}}]""",
                )
            ),
            null,
            FindingKind.CODE,
        ).findings.single()
        assertEquals(FindingSeverity.CRITICAL, entry.severity)
        assertEquals("security-severity=9.1", entry.rawSeverity)
    }

    @Test
    fun `Security severity of the rule`() {
        val entry = parser.parse(
            sarif(
                run(
                    result("A", extra = """"level": "note""""),
                    rules = """[{"id": "A", "properties": {"security-severity": "7.5"}}]""",
                )
            ),
            null,
            FindingKind.CODE,
        ).findings.single()
        assertEquals(FindingSeverity.HIGH, entry.severity)
        assertEquals("security-severity=7.5", entry.rawSeverity)
    }

    @Test
    fun `A security severity which is zero or not a number falls back to the level`() {
        listOf("\"0.0\"", "\"high\"", "0").forEach { score ->
            val entry = parser.parse(
                sarif(run(result("A", extra = """"level": "warning", "properties": {"security-severity": $score}"""))),
                null,
                FindingKind.CODE,
            ).findings.single()
            assertEquals(FindingSeverity.MEDIUM, entry.severity, "security-severity=$score")
            assertEquals("level=warning", entry.rawSeverity)
        }
    }

    @Test
    fun `Severity from the level`() {
        val cases = listOf(
            "error" to FindingSeverity.HIGH,
            "warning" to FindingSeverity.MEDIUM,
            "note" to FindingSeverity.LOW,
            "none" to FindingSeverity.UNKNOWN,
        )
        cases.forEach { (level, expected) ->
            val entry = parser.parse(
                sarif(run(result("A", extra = """"level": "$level""""))),
                null,
                FindingKind.CODE,
            ).findings.single()
            assertEquals(expected, entry.severity, "level=$level")
            assertEquals("level=$level", entry.rawSeverity)
        }
    }

    @Test
    fun `Level of the result before the default level of the rule`() {
        val entry = parser.parse(
            sarif(
                run(
                    result("A", extra = """"level": "error""""),
                    rules = """[{"id": "A", "defaultConfiguration": {"level": "note"}}]""",
                )
            ),
            null,
            FindingKind.CODE,
        ).findings.single()
        assertEquals(FindingSeverity.HIGH, entry.severity)
        assertEquals("level=error", entry.rawSeverity)
    }

    @Test
    fun `No severity at all is UNKNOWN`() {
        val entry = parser.parse(sarif(run(result("A"))), null, FindingKind.CODE).findings.single()
        assertEquals(FindingSeverity.UNKNOWN, entry.severity)
        assertNull(entry.rawSeverity)
    }

    @Test
    fun `An unknown level is UNKNOWN`() {
        val entry = parser.parse(
            sarif(run(result("A", extra = """"level": "fatal""""))),
            null,
            FindingKind.CODE,
        ).findings.single()
        assertEquals(FindingSeverity.UNKNOWN, entry.severity)
        assertEquals("level=fatal", entry.rawSeverity)
    }

    // ---------------------------------------------------------------------------------------------
    // Acceptance
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `A suppression accepted or without status is an acceptance, with its justification and no expiry`() {
        val report = parser.parse(
            sarif(
                run(
                    result("ACCEPTED", extra = """"suppressions": [{"kind": "external", "status": "accepted", "justification": "Test code only"}]"""),
                    result("NO-STATUS", extra = """"suppressions": [{"kind": "inSource", "justification": "False positive"}]"""),
                    result("NO-KIND", extra = """"suppressions": [{}]"""),
                    result(
                        "IN-FILE",
                        extra = """"suppressions": [{"kind": "external", "location": {"physicalLocation": {"artifactLocation": {"uri": ".semgrepignore"}, "region": {"startLine": 2}}}}]"""
                    ),
                )
            ),
            null,
            FindingKind.CODE,
        )
        assertEquals(
            listOf(
                FindingsReportAcceptance(statement = "Test code only", expiresAt = null, source = "SARIF suppression (external)"),
                FindingsReportAcceptance(statement = "False positive", expiresAt = null, source = "SARIF suppression (inSource)"),
                FindingsReportAcceptance(statement = "", expiresAt = null, source = "SARIF suppression"),
                FindingsReportAcceptance(statement = "", expiresAt = null, source = ".semgrepignore"),
            ),
            report.findings.map { it.acceptance }
        )
    }

    @Test
    fun `A suppression under review or rejected is not an acceptance`() {
        val report = parser.parse(
            sarif(
                run(
                    result("REVIEW", extra = """"suppressions": [{"kind": "external", "status": "underReview", "justification": "Maybe"}]"""),
                    result("REJECTED", extra = """"suppressions": [{"kind": "external", "status": "rejected", "justification": "No"}]"""),
                    result(
                        "MIXED",
                        extra = """"suppressions": [{"kind": "inSource", "status": "accepted"}, {"kind": "external", "status": "rejected"}]"""
                    ),
                    result("EMPTY", extra = """"suppressions": []"""),
                )
            ),
            null,
            FindingKind.CODE,
        )
        assertEquals(listOf(null, null, null, null), report.findings.map { it.acceptance })
    }

    // ---------------------------------------------------------------------------------------------
    // Building reports
    // ---------------------------------------------------------------------------------------------

    private fun sarif(vararg runs: String): JsonNode =
        """{"version": "2.1.0", "runs": [${runs.joinToString(",")}]}""".parseAsJson()

    private fun run(vararg results: String, toolName: String = "Tool", rules: String = "[]"): String =
        """{"tool": {"driver": {"name": "$toolName", "rules": $rules}}, "results": [${results.joinToString(",")}]}"""

    private fun result(
        ruleId: String?,
        locations: String? = null,
        extra: String? = null,
    ): String {
        val fields = listOfNotNull(
            ruleId?.let { """"ruleId": "$it"""" },
            """"message": {"text": "Some message"}""",
            locations?.let { """"locations": $it""" },
            extra,
        )
        return fields.joinToString(",", prefix = "{", postfix = "}")
    }
}
