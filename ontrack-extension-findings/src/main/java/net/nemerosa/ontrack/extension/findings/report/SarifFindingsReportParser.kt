package net.nemerosa.ontrack.extension.findings.report

import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

/**
 * Parser of [SARIF 2.1](https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html), the
 * `sarif` format, a native format.
 *
 * Every result of every run is a finding:
 *
 * - `externalId` is the `ruleId` of the result;
 * - `location` is the `artifactLocation.uri` of the first location of the result, without its
 *   region — no line, no column — so that several results of one rule in one file are one finding.
 *   Empty when there is none;
 * - `title` is the `shortDescription` of the rule, else its `name`; `url` is its `helpUri`;
 * - `severity` comes from `properties.security-severity` (of the result, else of the rule) by the
 *   thresholds of GitHub code scanning, else from the `level` (of the result, else the default one
 *   of the rule);
 * - the result is accepted when it carries a suppression accepted, or without status.
 *
 * The scanner is the name of the tool, lowercased, unless given by the caller. A SARIF report
 * cannot say what was scanned: the kind is always given by the caller, for every run.
 *
 * **Never read**: the `message` of a result, the `snippet` of a region, the `contextRegion` of a
 * location. That is where a secret scanner puts the value of the secret.
 *
 * Lenient on what is only descriptive, strict on what makes a finding: a result without any rule
 * id is rejected. An error names the offending field, never its value.
 */
@Component
class SarifFindingsReportParser : FindingsReportParser {

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
            val version = text(node.get("version"))
            if (version == null || !version.startsWith(SUPPORTED_VERSION)) {
                errors += "`version`: only SARIF 2.1 is supported"
            }
            if (kind == null) {
                errors += "`kind` is required as an argument, a SARIF report cannot give it"
            }
            val runs = node.get("runs")
            if (runs == null || !runs.isArray) {
                errors += "`runs`: required, as an array"
                return null
            }
            val actualScanner = scanner?.takeIf { it.isNotBlank() } ?: readScanner(runs)
            val findings = runs.values().flatMapIndexed { index, run ->
                readRun(run, "runs[$index]")
            }
            return if (actualScanner != null && kind != null) {
                ParsedFindingsReport(
                    scanner = actualScanner,
                    kind = kind,
                    findings = findings,
                )
            } else {
                null
            }
        }

        /**
         * The scanner is the name of the tool of the runs, lowercased, when they all have the same.
         */
        private fun readScanner(runs: JsonNode): String? {
            val names = runs.values().map { run ->
                text(run.path("tool").path("driver").get("name"))?.takeIf { it.isNotBlank() }?.lowercase()
            }
            return when {
                names.isEmpty() -> {
                    errors += "`scanner` is required as an argument when the report has no run"
                    null
                }

                names.any { it == null } -> {
                    errors += "`runs[].tool.driver.name`: required, or `scanner` as an argument"
                    null
                }

                names.distinct().size > 1 -> {
                    errors += "the runs come from different tools: `scanner` is required as an argument"
                    null
                }

                else -> names.first()
            }
        }

        private fun readRun(run: JsonNode, path: String): List<FindingsReportEntry> {
            if (!run.isObject) {
                errors += "`$path`: must be an object"
                return emptyList()
            }
            val rules = Rules(run.path("tool"))
            val artifacts = run.get("artifacts")?.takeIf { it.isArray }
            val results = run.get("results")
            return when {
                results == null || results.isNull -> emptyList()
                !results.isArray -> {
                    errors += "`$path.results`: must be an array"
                    emptyList()
                }

                else -> results.values().mapIndexedNotNull { index, result ->
                    readResult(result, "$path.results[$index]", rules, artifacts)
                }
            }
        }

        private fun readResult(
            result: JsonNode,
            path: String,
            rules: Rules,
            artifacts: JsonNode?,
        ): FindingsReportEntry? {
            if (!result.isObject) {
                errors += "`$path`: must be an object"
                return null
            }
            // Not a failure, not a finding
            if (text(result.get("kind")) in NOT_FAILURES || text(result.get("baselineState")) == BASELINE_ABSENT) {
                return null
            }
            // Rule
            val rule = rules.find(result)
            val ruleId = text(result.get("ruleId"))
                ?: text(result.path("rule").get("id"))
                ?: text(rule?.get("id"))
            if (ruleId.isNullOrBlank()) {
                errors += "`$path`: no rule id"
                return null
            }
            // Severity
            val (severity, rawSeverity) = readSeverity(result, rule)
            // Entry
            return FindingsReportEntry(
                externalId = ruleId,
                location = readLocation(result, artifacts),
                severity = severity,
                rawSeverity = rawSeverity,
                title = text(rule?.path("shortDescription")?.get("text"))?.takeIf { it.isNotBlank() }
                    ?: text(rule?.get("name"))?.takeIf { it.isNotBlank() }
                    ?: ruleId,
                url = text(rule?.get("helpUri"))?.takeIf { it.isNotBlank() },
                acceptance = readAcceptance(result),
            )
        }

        /**
         * The URI of the artifact of the first location, never its region.
         */
        private fun readLocation(result: JsonNode, artifacts: JsonNode?): String {
            val artifactLocation = result.path("locations").path(0).path("physicalLocation").path("artifactLocation")
            return text(artifactLocation.get("uri"))
                ?: artifactLocation.get("index")?.takeIf { it.isInt }?.let { index ->
                    text(artifacts?.path(index.intValue())?.path("location")?.get("uri"))
                }
                ?: ""
        }

        private fun readSeverity(result: JsonNode, rule: JsonNode?): Pair<FindingSeverity, String?> {
            // Security severity
            val securitySeverity = listOfNotNull(result, rule)
                .firstNotNullOfOrNull { securitySeverity(it) }
            if (securitySeverity != null) {
                val (raw, score) = securitySeverity
                val severity = when {
                    score >= 9.0 -> FindingSeverity.CRITICAL
                    score >= 7.0 -> FindingSeverity.HIGH
                    score >= 4.0 -> FindingSeverity.MEDIUM
                    else -> FindingSeverity.LOW
                }
                return severity to "$SECURITY_SEVERITY=$raw"
            }
            // Level
            val level = text(result.get("level"))
                ?: text(rule?.path("defaultConfiguration")?.get("level"))
                ?: return FindingSeverity.UNKNOWN to null
            val severity = when (level) {
                "error" -> FindingSeverity.HIGH
                "warning" -> FindingSeverity.MEDIUM
                "note" -> FindingSeverity.LOW
                else -> FindingSeverity.UNKNOWN
            }
            return severity to "level=$level"
        }

        /**
         * A security severity is a score, given as a string or as a number. Only a positive score
         * counts: otherwise, the level is used.
         */
        private fun securitySeverity(node: JsonNode): Pair<String, Double>? {
            val value = node.path("properties").get(SECURITY_SEVERITY) ?: return null
            val raw = when {
                value.isString -> value.stringValue().trim()
                value.isNumber -> value.asString()
                else -> return null
            }
            val score = raw.toDoubleOrNull() ?: return null
            return if (score > 0) raw to score else null
        }

        /**
         * A result is accepted when one of its suppressions is accepted — or has no status — and
         * none is under review or rejected (SARIF 2.1, §3.27.23). SARIF has no expiry.
         */
        private fun readAcceptance(result: JsonNode): FindingsReportAcceptance? {
            val suppressions = result.get("suppressions")?.takeIf { it.isArray }?.values()
                ?.filter { it.isObject }
                ?: return null
            val statuses = suppressions.map { text(it.get("status")) }
            if (statuses.any { it == STATUS_UNDER_REVIEW || it == STATUS_REJECTED }) {
                return null
            }
            val suppression = suppressions.firstOrNull {
                text(it.get("status")).let { status -> status == null || status == STATUS_ACCEPTED }
            } ?: return null
            val kind = text(suppression.get("kind"))
            return FindingsReportAcceptance(
                statement = text(suppression.get("justification")) ?: "",
                expiresAt = null,
                source = text(
                    suppression.path("location").path("physicalLocation").path("artifactLocation").get("uri")
                )?.takeIf { it.isNotBlank() }
                    ?: if (kind != null) "$SUPPRESSION_SOURCE ($kind)" else SUPPRESSION_SOURCE,
            )
        }

        private fun text(node: JsonNode?): String? =
            node?.takeIf { it.isString }?.stringValue()
    }

    /**
     * Rules of a run: those of its driver, and those of its extensions — CodeQL puts its rules in
     * the query packs, as extensions.
     */
    private class Rules(tool: JsonNode) {

        private val driver: List<JsonNode> = rules(tool.path("driver"))

        private val extensions: List<List<JsonNode>> =
            tool.get("extensions")?.takeIf { it.isArray }?.values()?.map { rules(it) } ?: emptyList()

        private val byId: Map<String, JsonNode> = (driver + extensions.flatten())
            .mapNotNull { rule -> rule.get("id")?.takeIf { it.isString }?.let { it.stringValue() to rule } }
            .reversed() // The first one wins
            .toMap()

        private fun rules(component: JsonNode): List<JsonNode> =
            component.get("rules")?.takeIf { it.isArray }?.values()?.filter { it.isObject } ?: emptyList()

        /**
         * Rule of a result: by its index in its tool component, else by its id — a hierarchical
         * id (`CA2101/1`) falling back to its root.
         */
        fun find(result: JsonNode): JsonNode? {
            val reference = result.path("rule")
            val componentIndex = reference.path("toolComponent").get("index")?.takeIf { it.isInt }?.intValue()
            val component = if (componentIndex != null) extensions.getOrNull(componentIndex) else driver
            val index = (reference.get("index") ?: result.get("ruleIndex"))?.takeIf { it.isInt }?.intValue()
            val id = result.get("ruleId")?.takeIf { it.isString }?.stringValue()
                ?: reference.get("id")?.takeIf { it.isString }?.stringValue()
            val indexed = if (index != null && component != null) component.getOrNull(index) else null
            return indexed
                ?: id?.let { byId[it] ?: byId[it.substringBefore('/')] }
        }
    }

    companion object {
        /**
         * Name of the SARIF format
         */
        const val FORMAT = "sarif"

        private const val SUPPORTED_VERSION = "2.1"

        private const val SECURITY_SEVERITY = "security-severity"

        private val NOT_FAILURES = setOf("pass", "notApplicable")
        private const val BASELINE_ABSENT = "absent"

        private const val STATUS_ACCEPTED = "accepted"
        private const val STATUS_UNDER_REVIEW = "underReview"
        private const val STATUS_REJECTED = "rejected"

        private const val SUPPRESSION_SOURCE = "SARIF suppression"
    }
}
