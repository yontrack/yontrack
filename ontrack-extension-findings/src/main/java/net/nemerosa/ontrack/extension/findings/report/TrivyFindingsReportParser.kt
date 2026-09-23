package net.nemerosa.ontrack.extension.findings.report

import net.nemerosa.ontrack.extension.findings.location.FindingLocations
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

/**
 * Parser of [Trivy JSON](https://trivy.dev/latest/docs/configuration/reporting/#json), the `trivy`
 * format, a native format — the output of `trivy <target> --format json`, schema version 2.
 *
 * **Only the vulnerabilities are read**, `Results[].Vulnerabilities[]`. The `Secrets`, the
 * `Misconfigurations` and the `Licenses` of the results are never read: Trivy's secret results
 * carry the matched text.
 *
 * Every vulnerability is a finding:
 *
 * - `externalId` is the `VulnerabilityID`;
 * - `location` is the purl of the package, `PkgIdentifier.PURL`, without its version and its
 *   qualifiers. A package without any purl — older versions of Trivy, some package types — falls
 *   back to its `PkgName`, which is **not a purl**;
 * - `installedVersion` is the `InstalledVersion`, else the version of the purl;
 * - `severity` is the `Severity`, UNKNOWN being native to Trivy; `rawSeverity` is the severity with
 *   the source it comes from, `HIGH (nvd)`;
 * - `title` is the `Title`, else the ID; `url` is the `PrimaryURL`; `fixedVersion` is the
 *   `FixedVersion`.
 *
 * Unlike Trivy's SARIF, which gives the path of the package or the target and not its purl, one
 * CVE in two packages is two findings.
 *
 * **Acceptance**: with `--show-suppressed`, Trivy moves the vulnerabilities suppressed by an
 * ignore file or a VEX document to the `ExperimentalModifiedFindings` of their result. A modified
 * vulnerability whose status is `ignored` or `not_affected` is a finding, accepted with the
 * `Statement` and the `Source` of the modification, without any expiry — Trivy has none. Any other
 * modification — a VEX saying `fixed` — is not read: Trivy does not report the vulnerability.
 *
 * The scanner is `trivy`, unless given by the caller. A Trivy report does not say what was scanned
 * in the terms of Yontrack: the kind is always given by the caller.
 *
 * Lenient on what is only descriptive, strict on what makes a finding: a vulnerability without any
 * ID or without any package is rejected. An error names the offending field, never its value.
 */
@Component
class TrivyFindingsReportParser : FindingsReportParser {

    override val format: String = FORMAT

    override val nativeFormat: Boolean = true

    override fun parse(report: JsonNode, scanner: String?, kind: FindingKind?): ParsedFindingsReport {
        val reader = Reader()
        val parsed = reader.readReport(report, scanner, kind)
        if (reader.errors.isNotEmpty() || parsed == null) {
            throw FindingsReportFormatException(FORMAT, reader.errors)
        }
        return parsed
    }

    private class Reader {

        val errors = mutableListOf<String>()

        fun readReport(node: JsonNode, scanner: String?, kind: FindingKind?): ParsedFindingsReport? {
            if (!node.isObject) {
                errors += "the report must be a JSON object"
                return null
            }
            val schemaVersion = node.get("SchemaVersion")
            if (schemaVersion == null || !schemaVersion.isInt || schemaVersion.intValue() != SUPPORTED_SCHEMA_VERSION) {
                errors += "`SchemaVersion`: only the schema version $SUPPORTED_SCHEMA_VERSION of Trivy JSON is supported"
            }
            if (kind == null) {
                errors += "`kind` is required as an argument, a Trivy report cannot give it"
            }
            val results = node.get("Results")
            val findings = when {
                results == null || results.isNull -> emptyList()
                !results.isArray -> {
                    errors += "`Results`: must be an array"
                    emptyList()
                }

                else -> results.values().flatMapIndexed { index, result ->
                    readResult(result, "Results[$index]")
                }
            }
            return if (kind != null) {
                ParsedFindingsReport(
                    scanner = scanner?.takeIf { it.isNotBlank() } ?: SCANNER,
                    kind = kind,
                    findings = findings,
                )
            } else {
                null
            }
        }

        /**
         * The vulnerabilities of a result, then its accepted ones. Never its secrets, its
         * misconfigurations or its licences.
         */
        private fun readResult(result: JsonNode, path: String): List<FindingsReportEntry> {
            if (!result.isObject) {
                errors += "`$path`: must be an object"
                return emptyList()
            }
            val vulnerabilities = array(result, "Vulnerabilities", path)
                .mapIndexedNotNull { index, vulnerability ->
                    readVulnerability(vulnerability, "$path.Vulnerabilities[$index]", acceptance = null)
                }
            val accepted = array(result, "ExperimentalModifiedFindings", path)
                .mapIndexedNotNull { index, modified ->
                    readModifiedFinding(modified, "$path.ExperimentalModifiedFindings[$index]")
                }
            return vulnerabilities + accepted
        }

        /**
         * A modified finding is read only when it is a vulnerability, ignored or not affected. The
         * type is checked before anything else: a modified secret carries the matched text.
         */
        private fun readModifiedFinding(modified: JsonNode, path: String): FindingsReportEntry? {
            if (!modified.isObject || text(modified.get("Type")) != TYPE_VULNERABILITY) {
                return null
            }
            val status = text(modified.get("Status"))
            if (status !in ACCEPTED_STATUSES) {
                return null
            }
            val finding = modified.get("Finding")?.takeIf { it.isObject } ?: return null
            return readVulnerability(
                finding,
                "$path.Finding",
                acceptance = FindingsReportAcceptance(
                    statement = text(modified.get("Statement")) ?: "",
                    expiresAt = null,
                    source = text(modified.get("Source"))?.takeIf { it.isNotBlank() } ?: "$SCANNER_NAME ($status)",
                ),
            )
        }

        private fun readVulnerability(
            vulnerability: JsonNode,
            path: String,
            acceptance: FindingsReportAcceptance?,
        ): FindingsReportEntry? {
            if (!vulnerability.isObject) {
                errors += "`$path`: must be an object"
                return null
            }
            val id = nonBlank(vulnerability.get("VulnerabilityID"))
            if (id == null) {
                errors += "`$path`: no `VulnerabilityID`"
                return null
            }
            // Package
            val purl = nonBlank(vulnerability.path("PkgIdentifier").get("PURL"))
                ?.let { FindingLocations.normalise(it) }
            val location = purl?.location ?: nonBlank(vulnerability.get("PkgName"))
            if (location == null) {
                errors += "`$path`: no package, neither `PkgIdentifier.PURL` nor `PkgName`"
                return null
            }
            // Severity
            val (severity, rawSeverity) = readSeverity(vulnerability)
            // Entry
            return FindingsReportEntry(
                externalId = id,
                location = location,
                severity = severity,
                rawSeverity = rawSeverity,
                title = nonBlank(vulnerability.get("Title")) ?: id,
                url = nonBlank(vulnerability.get("PrimaryURL")),
                fixedVersion = nonBlank(vulnerability.get("FixedVersion")),
                installedVersion = nonBlank(vulnerability.get("InstalledVersion")) ?: purl?.version,
                acceptance = acceptance,
            )
        }

        /**
         * The severity of Trivy, which has UNKNOWN natively, and the source it comes from.
         */
        private fun readSeverity(vulnerability: JsonNode): Pair<FindingSeverity, String?> {
            val raw = nonBlank(vulnerability.get("Severity"))
                ?: return FindingSeverity.UNKNOWN to null
            val severity = FindingSeverity.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: FindingSeverity.UNKNOWN
            val source = nonBlank(vulnerability.get("SeveritySource"))
            return severity to if (source != null) "$raw ($source)" else raw
        }

        private fun array(node: JsonNode, field: String, path: String): List<JsonNode> {
            val array = node.get(field)
            return when {
                array == null || array.isNull -> emptyList()
                !array.isArray -> {
                    errors += "`$path.$field`: must be an array"
                    emptyList()
                }

                else -> array.values().toList()
            }
        }

        private fun text(node: JsonNode?): String? =
            node?.takeIf { it.isString }?.stringValue()

        private fun nonBlank(node: JsonNode?): String? =
            text(node)?.takeIf { it.isNotBlank() }
    }

    companion object {
        /**
         * Name of the Trivy JSON format
         */
        const val FORMAT = "trivy"

        /**
         * Scanner of the findings, unless given by the caller
         */
        const val SCANNER = "trivy"

        private const val SCANNER_NAME = "Trivy"

        private const val SUPPORTED_SCHEMA_VERSION = 2

        private const val TYPE_VULNERABILITY = "vulnerability"

        private val ACCEPTED_STATUSES = setOf("ignored", "not_affected")
    }
}
