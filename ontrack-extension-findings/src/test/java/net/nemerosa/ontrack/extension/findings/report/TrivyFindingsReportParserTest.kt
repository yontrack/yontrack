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

class TrivyFindingsReportParserTest {

    private val parser = TrivyFindingsReportParser()

    /**
     * Output of `trivy image --format json --show-suppressed` on an image with OS packages, Java
     * libraries, a secret, a misconfiguration and a restricted licence.
     */
    private val image: JsonNode by lazy { TestUtils.resourceJson("/trivy/image.json") }

    @Test
    fun `The format is trivy, a native format`() {
        assertEquals("trivy", parser.format)
        assertTrue(parser.nativeFormat)
    }

    // ---------------------------------------------------------------------------------------------
    // Sample
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `Image sample`() {
        val report = parser.parse(image, scanner = null, kind = FindingKind.IMAGE)
        assertEquals("trivy", report.scanner)
        assertEquals(FindingKind.IMAGE, report.kind)
        assertEquals(
            listOf(
                // The same CVE in two packages
                FindingsReportEntry(
                    externalId = "CVE-2023-5363",
                    location = "pkg:deb/debian/libssl3",
                    severity = FindingSeverity.HIGH,
                    rawSeverity = "HIGH (nvd)",
                    title = "openssl: Incorrect cipher key and IV length processing",
                    url = "https://avd.aquasec.com/nvd/cve-2023-5363",
                    fixedVersion = "3.0.11-1~deb12u2",
                    installedVersion = "3.0.9-1",
                ),
                FindingsReportEntry(
                    externalId = "CVE-2023-5363",
                    location = "pkg:deb/debian/openssl",
                    severity = FindingSeverity.HIGH,
                    rawSeverity = "HIGH (nvd)",
                    title = "openssl: Incorrect cipher key and IV length processing",
                    url = "https://avd.aquasec.com/nvd/cve-2023-5363",
                    fixedVersion = "3.0.11-1~deb12u2",
                    installedVersion = "3.0.9-1",
                ),
                // No fix
                FindingsReportEntry(
                    externalId = "CVE-2023-4039",
                    location = "pkg:deb/debian/libgcc-s1",
                    severity = FindingSeverity.MEDIUM,
                    rawSeverity = "MEDIUM (nvd)",
                    title = "gcc: -fstack-protector fails to guard dynamic stack allocations on ARM64",
                    url = "https://avd.aquasec.com/nvd/cve-2023-4039",
                    fixedVersion = null,
                    installedVersion = "12.2.0-14",
                ),
                // No title
                FindingsReportEntry(
                    externalId = "TEMP-0841856-B18BAF",
                    location = "pkg:deb/debian/bash",
                    severity = FindingSeverity.LOW,
                    rawSeverity = "LOW (debian)",
                    title = "TEMP-0841856-B18BAF",
                    url = "https://security-tracker.debian.org/tracker/TEMP-0841856-B18BAF",
                    fixedVersion = null,
                    installedVersion = "5.2.15-2+b2",
                ),
                // No severity source
                FindingsReportEntry(
                    externalId = "CVE-2025-31115",
                    location = "pkg:deb/debian/liblzma5",
                    severity = FindingSeverity.UNKNOWN,
                    rawSeverity = "UNKNOWN",
                    title = "xz: XZ has a heap-use-after-free bug in threaded .xz decoder",
                    url = "https://avd.aquasec.com/nvd/cve-2025-31115",
                    fixedVersion = "5.4.1-1",
                    installedVersion = "5.4.1-0.2",
                ),
                // Suppressed by an ignore file
                FindingsReportEntry(
                    externalId = "CVE-2023-45853",
                    location = "pkg:deb/debian/zlib1g",
                    severity = FindingSeverity.CRITICAL,
                    rawSeverity = "CRITICAL (nvd)",
                    title = "zlib: integer overflow and resultant heap-based buffer overflow in zipOpenNewFileInZip4_6",
                    url = "https://avd.aquasec.com/nvd/cve-2023-45853",
                    fixedVersion = null,
                    installedVersion = "1:1.2.13.dfsg-1",
                    acceptance = FindingsReportAcceptance(
                        statement = "Not exploitable: the image never creates zip archives",
                        expiresAt = null,
                        source = ".trivyignore.yaml",
                    ),
                ),
                FindingsReportEntry(
                    externalId = "CVE-2021-44228",
                    location = "pkg:maven/org.apache.logging.log4j/log4j-core",
                    severity = FindingSeverity.CRITICAL,
                    rawSeverity = "CRITICAL (ghsa)",
                    title = "log4j-core: Remote code execution in Log4j 2.x when logs contain an attacker-controlled string value",
                    url = "https://avd.aquasec.com/nvd/cve-2021-44228",
                    fixedVersion = "2.15.0, 2.3.1, 2.12.2",
                    installedVersion = "2.14.1",
                ),
                // No purl: the package name
                FindingsReportEntry(
                    externalId = "CVE-2022-42003",
                    location = "com.fasterxml.jackson.core:jackson-databind",
                    severity = FindingSeverity.HIGH,
                    rawSeverity = "HIGH (ghsa)",
                    title = "jackson-databind: deep wrapper array nesting wrt UNWRAP_SINGLE_VALUE_ARRAYS",
                    url = "https://avd.aquasec.com/nvd/cve-2022-42003",
                    fixedVersion = "2.12.7.1, 2.13.4.2",
                    installedVersion = "2.13.4",
                ),
                // Suppressed by a VEX document
                FindingsReportEntry(
                    externalId = "CVE-2022-1471",
                    location = "pkg:maven/org.yaml/snakeyaml",
                    severity = FindingSeverity.HIGH,
                    rawSeverity = "HIGH (ghsa)",
                    title = "SnakeYaml: Constructor Deserialization Remote Code Execution",
                    url = "https://avd.aquasec.com/nvd/cve-2022-1471",
                    fixedVersion = "2.0",
                    installedVersion = "1.33",
                    acceptance = FindingsReportAcceptance(
                        statement = "vulnerable_code_not_in_execute_path",
                        expiresAt = null,
                        source = "vex/orders.openvex.json",
                    ),
                ),
            ),
            report.findings
        )
    }

    @Test
    fun `Image sample - the same CVE in two packages is two findings, and a suppressed finding is accepted`() {
        val report = parser.parse(image, scanner = null, kind = FindingKind.IMAGE)
        val findings = FindingsConsolidation.consolidate(report.findings, LocalDate.of(2026, 9, 23))
        assertEquals(9, findings.size)
        assertEquals(
            listOf("pkg:deb/debian/libssl3", "pkg:deb/debian/openssl"),
            findings.filter { it.externalId == "CVE-2023-5363" }.map { it.location }
        )
        assertEquals(
            setOf("CVE-2023-45853", "CVE-2022-1471"),
            findings.filter { it.accepted }.map { it.externalId }.toSet()
        )
    }

    @Test
    fun `Secrets, misconfigurations and licences are never read`() {
        val text = parser.parse(image, scanner = null, kind = FindingKind.IMAGE).asJson().toString()
        listOf(
            // Secrets, reported or suppressed
            "AKIAIOSFODNN7EXAMPLE",
            "wJalrXUtnFEMI",
            "aws-access-key-id",
            "aws-secret-access-key",
            // Misconfigurations
            "DS002",
            "root",
            // Licences
            "GPL-3.0",
            "libreadline8",
        ).forEach { token ->
            assertFalse(token in text, "`$token` must not be read from the sample")
        }
    }

    @Test
    fun `A modified finding which is not ignored nor not affected is not read`() {
        val report = parser.parse(image, scanner = null, kind = FindingKind.IMAGE)
        assertTrue(report.findings.none { it.externalId == "CVE-2023-2976" })
    }

    // ---------------------------------------------------------------------------------------------
    // Scanner and kind
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `The scanner is trivy unless given by the caller`() {
        assertEquals("trivy", parser.parse(trivy(), scanner = null, kind = FindingKind.IMAGE).scanner)
        assertEquals("trivy", parser.parse(trivy(), scanner = " ", kind = FindingKind.IMAGE).scanner)
        assertEquals("trivy-fs", parser.parse(trivy(), scanner = "trivy-fs", kind = FindingKind.IMAGE).scanner)
    }

    @Test
    fun `The kind is required from the caller`() {
        val ex = assertThrows<FindingsReportFormatException> {
            parser.parse(image, scanner = null, kind = null)
        }
        assertEquals(
            "Invalid findings report (format `trivy`): `kind` is required as an argument, a Trivy report cannot give it",
            ex.message
        )
    }

    @Test
    fun `The kind given by the caller applies to every finding`() {
        val report = parser.parse(
            trivy(result(vulnerability("CVE-1")), result(vulnerability("CVE-2"))),
            scanner = null,
            kind = FindingKind.DEPENDENCIES,
        )
        assertEquals(FindingKind.DEPENDENCIES, report.kind)
        assertEquals(listOf("CVE-1", "CVE-2"), report.findings.map { it.externalId })
    }

    // ---------------------------------------------------------------------------------------------
    // Structure
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `A report without results or without vulnerabilities is a scan without findings`() {
        assertTrue(parser.parse(trivy(), null, FindingKind.IMAGE).findings.isEmpty())
        assertTrue(parser.parse("""{"SchemaVersion": 2}""".parseAsJson(), null, FindingKind.IMAGE).findings.isEmpty())
        assertTrue(
            parser.parse("""{"SchemaVersion": 2, "Results": null}""".parseAsJson(), null, FindingKind.IMAGE).findings.isEmpty()
        )
        assertTrue(
            parser.parse(trivy("""{"Target": "Python", "Class": "lang-pkgs"}"""), null, FindingKind.IMAGE).findings.isEmpty()
        )
    }

    @Test
    fun `A report which is not Trivy JSON is rejected`() {
        assertEquals(
            "Invalid findings report (format `trivy`): the report must be a JSON object",
            assertThrows<FindingsReportFormatException> {
                parser.parse("[]".parseAsJson(), null, FindingKind.IMAGE)
            }.message
        )
        assertEquals(
            "Invalid findings report (format `trivy`): `SchemaVersion`: only the schema version 2 of Trivy JSON is supported",
            assertThrows<FindingsReportFormatException> {
                parser.parse("""{"version": "2.1.0", "runs": []}""".parseAsJson(), null, FindingKind.IMAGE)
            }.message
        )
        assertEquals(
            "Invalid findings report (format `trivy`): `Results`: must be an array",
            assertThrows<FindingsReportFormatException> {
                parser.parse("""{"SchemaVersion": 2, "Results": {}}""".parseAsJson(), null, FindingKind.IMAGE)
            }.message
        )
        assertEquals(
            "Invalid findings report (format `trivy`): `Results[0].Vulnerabilities`: must be an array",
            assertThrows<FindingsReportFormatException> {
                parser.parse(trivy("""{"Vulnerabilities": {}}"""), null, FindingKind.IMAGE)
            }.message
        )
    }

    @Test
    fun `A vulnerability without any ID is rejected, naming the field and not its value`() {
        val ex = assertThrows<FindingsReportFormatException> {
            parser.parse(
                trivy(result(vulnerability("CVE-1"), vulnerability(null, pkgName = "secret-package"))),
                null,
                FindingKind.IMAGE,
            )
        }
        assertEquals(
            "Invalid findings report (format `trivy`): `Results[0].Vulnerabilities[1]`: no `VulnerabilityID`",
            ex.message
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Location and versions
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `The location is the purl without its version and qualifiers, else the package name`() {
        val report = parser.parse(
            trivy(
                result(
                    vulnerability("PURL", purl = "pkg:npm/%40angular/core@16.2.0?foo=bar", pkgName = "@angular/core"),
                    vulnerability("PURL-SUBPATH", purl = "pkg:golang/golang.org/x/net@v0.17.0#http2", pkgName = "golang.org/x/net"),
                    vulnerability("PKG-NAME", pkgName = "golang.org/x/crypto"),
                    vulnerability("EMPTY-PURL", purl = "", pkgName = "golang.org/x/text"),
                )
            ),
            null,
            FindingKind.DEPENDENCIES,
        )
        assertEquals(
            listOf(
                "pkg:npm/%40angular/core",
                "pkg:golang/golang.org/x/net#http2",
                "golang.org/x/crypto",
                "golang.org/x/text",
            ),
            report.findings.map { it.location }
        )
    }

    @Test
    fun `A vulnerability without any package is rejected`() {
        val ex = assertThrows<FindingsReportFormatException> {
            parser.parse(trivy(result(vulnerability("CVE-1", pkgName = null))), null, FindingKind.IMAGE)
        }
        assertEquals(
            "Invalid findings report (format `trivy`): `Results[0].Vulnerabilities[0]`: no package, neither `PkgIdentifier.PURL` nor `PkgName`",
            ex.message
        )
    }

    @Test
    fun `The installed version is the one of Trivy, else the version of the purl`() {
        val report = parser.parse(
            trivy(
                result(
                    vulnerability("INSTALLED", purl = "pkg:maven/org.x/y@1.0.0", installedVersion = "1.0.0-patched"),
                    vulnerability("PURL", purl = "pkg:maven/org.x/y@1.0.0"),
                    vulnerability("NONE", pkgName = "y"),
                )
            ),
            null,
            FindingKind.DEPENDENCIES,
        )
        assertEquals(listOf("1.0.0-patched", "1.0.0", null), report.findings.map { it.installedVersion })
    }

    // ---------------------------------------------------------------------------------------------
    // Severity
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `Severity from the Trivy severity, with its source`() {
        val report = parser.parse(
            trivy(
                result(
                    vulnerability("C", severity = "CRITICAL", severitySource = "nvd"),
                    vulnerability("H", severity = "HIGH", severitySource = "ghsa"),
                    vulnerability("M", severity = "MEDIUM", severitySource = "redhat"),
                    vulnerability("L", severity = "LOW", severitySource = "debian"),
                    vulnerability("U", severity = "UNKNOWN"),
                    vulnerability("NO-SOURCE", severity = "HIGH"),
                    vulnerability("BLANK-SOURCE", severity = "HIGH", severitySource = ""),
                )
            ),
            null,
            FindingKind.IMAGE,
        )
        assertEquals(
            listOf(
                FindingSeverity.CRITICAL to "CRITICAL (nvd)",
                FindingSeverity.HIGH to "HIGH (ghsa)",
                FindingSeverity.MEDIUM to "MEDIUM (redhat)",
                FindingSeverity.LOW to "LOW (debian)",
                FindingSeverity.UNKNOWN to "UNKNOWN",
                FindingSeverity.HIGH to "HIGH",
                FindingSeverity.HIGH to "HIGH",
            ),
            report.findings.map { it.severity to it.rawSeverity }
        )
    }

    @Test
    fun `A missing or unknown severity is UNKNOWN`() {
        val report = parser.parse(
            trivy(
                result(
                    vulnerability("NONE", severity = null),
                    vulnerability("OTHER", severity = "IMPORTANT", severitySource = "vendor"),
                    vulnerability("LOWERCASE", severity = "high", severitySource = "nvd"),
                )
            ),
            null,
            FindingKind.IMAGE,
        )
        assertEquals(
            listOf(
                FindingSeverity.UNKNOWN to null,
                FindingSeverity.UNKNOWN to "IMPORTANT (vendor)",
                FindingSeverity.HIGH to "high (nvd)",
            ),
            report.findings.map { it.severity to it.rawSeverity }
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Title, URL, fixed version
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `Title, URL and fixed version, the title defaulting to the ID`() {
        val report = parser.parse(
            trivy(
                result(
                    vulnerability("FULL", title = "Some title", url = "https://example.com/full", fixedVersion = "2.0"),
                    vulnerability("BARE"),
                    vulnerability("BLANK", title = " ", url = "", fixedVersion = ""),
                )
            ),
            null,
            FindingKind.IMAGE,
        )
        assertEquals(
            listOf(
                Triple("Some title", "https://example.com/full", "2.0"),
                Triple("BARE", null, null),
                Triple("BLANK", null, null),
            ),
            report.findings.map { Triple(it.title, it.url, it.fixedVersion) }
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Acceptance
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `A vulnerability ignored or not affected is accepted, with its statement and source and no expiry`() {
        val report = parser.parse(
            trivy(
                result(
                    vulnerability("OPEN"),
                    modified = listOf(
                        modified("ignored", vulnerability("IGNORED"), statement = "Accepted risk", source = ".trivyignore"),
                        modified("not_affected", vulnerability("NOT-AFFECTED"), statement = "component_not_present", source = "vex.json"),
                        modified("ignored", vulnerability("BARE"), statement = null, source = null),
                    ),
                )
            ),
            null,
            FindingKind.IMAGE,
        )
        assertEquals(
            listOf(
                "OPEN" to null,
                "IGNORED" to FindingsReportAcceptance(statement = "Accepted risk", expiresAt = null, source = ".trivyignore"),
                "NOT-AFFECTED" to FindingsReportAcceptance(statement = "component_not_present", expiresAt = null, source = "vex.json"),
                "BARE" to FindingsReportAcceptance(statement = "", expiresAt = null, source = "Trivy (ignored)"),
            ),
            report.findings.map { it.externalId to it.acceptance }
        )
    }

    @Test
    fun `Only the modified vulnerabilities ignored or not affected are read`() {
        val report = parser.parse(
            trivy(
                result(
                    modified = listOf(
                        modified("fixed", vulnerability("FIXED")),
                        modified("under_investigation", vulnerability("INVESTIGATED")),
                        modified("affected", vulnerability("AFFECTED")),
                        modified(null, vulnerability("NO-STATUS")),
                        modified("ignored", vulnerability("SECRET"), type = "secret"),
                        modified("ignored", vulnerability("MISCONFIG"), type = "misconfiguration"),
                        modified("ignored", vulnerability("LICENSE"), type = "license"),
                        modified("ignored", null),
                        modified("ignored", vulnerability("KEPT")),
                    ),
                )
            ),
            null,
            FindingKind.IMAGE,
        )
        assertEquals(listOf("KEPT"), report.findings.map { it.externalId })
    }

    @Test
    fun `A vulnerability which is not modified is not accepted`() {
        val report = parser.parse(trivy(result(vulnerability("CVE-1"))), null, FindingKind.IMAGE)
        assertNull(report.findings.single().acceptance)
    }

    // ---------------------------------------------------------------------------------------------
    // Building reports
    // ---------------------------------------------------------------------------------------------

    private fun trivy(vararg results: String): JsonNode =
        """{"SchemaVersion": 2, "ArtifactName": "app", "ArtifactType": "container_image", "Results": [${results.joinToString(",")}]}""".parseAsJson()

    private fun result(vararg vulnerabilities: String, modified: List<String> = emptyList()): String {
        val fields = listOfNotNull(
            """"Target": "app (debian 12.1)"""",
            """"Class": "os-pkgs"""",
            vulnerabilities.takeIf { it.isNotEmpty() }?.joinToString(",", prefix = """"Vulnerabilities": [""", postfix = "]"),
            modified.takeIf { it.isNotEmpty() }?.joinToString(",", prefix = """"ExperimentalModifiedFindings": [""", postfix = "]"),
        )
        return fields.joinToString(",", prefix = "{", postfix = "}")
    }

    private fun vulnerability(
        id: String?,
        pkgName: String? = "pkg",
        purl: String? = null,
        installedVersion: String? = null,
        severity: String? = "HIGH",
        severitySource: String? = null,
        title: String? = null,
        url: String? = null,
        fixedVersion: String? = null,
    ): String {
        val fields = listOfNotNull(
            id?.let { """"VulnerabilityID": "$it"""" },
            pkgName?.let { """"PkgName": "$it"""" },
            purl?.let { """"PkgIdentifier": {"PURL": "$it"}""" },
            installedVersion?.let { """"InstalledVersion": "$it"""" },
            severity?.let { """"Severity": "$it"""" },
            severitySource?.let { """"SeveritySource": "$it"""" },
            title?.let { """"Title": "$it"""" },
            url?.let { """"PrimaryURL": "$it"""" },
            fixedVersion?.let { """"FixedVersion": "$it"""" },
            """"Description": "Some description"""",
        )
        return fields.joinToString(",", prefix = "{", postfix = "}")
    }

    private fun modified(
        status: String?,
        finding: String?,
        type: String = "vulnerability",
        statement: String? = "Some statement",
        source: String? = "Some source",
    ): String {
        val fields = listOfNotNull(
            """"Type": "$type"""",
            status?.let { """"Status": "$it"""" },
            statement?.let { """"Statement": "$it"""" },
            source?.let { """"Source": "$it"""" },
            finding?.let { """"Finding": $it""" },
        )
        return fields.joinToString(",", prefix = "{", postfix = "}")
    }
}
