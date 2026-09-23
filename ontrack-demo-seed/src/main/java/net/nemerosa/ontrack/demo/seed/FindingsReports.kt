package net.nemerosa.ontrack.demo.seed

import net.nemerosa.ontrack.json.asJson
import tools.jackson.databind.JsonNode
import java.time.LocalDate

/**
 * Renders the report of a [ScanSpec] in its format, as a scanner would have written it.
 *
 * The dataset declares findings, not reports: which format carries them is a detail of how the
 * scan is posted, and writing the same finding twice in two formats by hand would be the place
 * for the two to drift apart.
 */
object FindingsReports {

    /**
     * @param scan Scan to render
     * @param today Day of the reset, which the expiry of an acceptance counts from
     */
    fun render(scan: ScanSpec, today: LocalDate): JsonNode = when (scan.format) {
        ScanFormat.FINDINGS -> neutral(scan, today)
        ScanFormat.SARIF -> sarif(scan)
    }

    /**
     * The neutral format of Yontrack. An absent optional field is left out rather than written as
     * `null`: the schema of the format rejects what it does not know, and is the one to trust.
     */
    private fun neutral(scan: ScanSpec, today: LocalDate): JsonNode = mapOf(
        "scanner" to scan.scanner,
        "kind" to scan.kind.name,
        "findings" to scan.findings.map { finding ->
            listOfNotNull(
                "externalId" to finding.externalId,
                "location" to finding.location,
                "severity" to finding.severity.name,
                "title" to finding.title,
                finding.url?.let { "url" to it },
                finding.installedVersion?.let { "installedVersion" to it },
                finding.fixedVersion?.let { "fixedVersion" to it },
                finding.acceptance?.let { acceptance ->
                    "acceptance" to listOfNotNull(
                        "statement" to acceptance.statement,
                        "source" to acceptance.source,
                        acceptance.expiresInDays?.let { "expiresAt" to today.plusDays(it).toString() },
                    ).toMap()
                },
            ).toMap()
        },
    ).asJson()

    /**
     * SARIF 2.1, one run, the scanner as the name of its tool.
     *
     * The severity goes in the `security-severity` of the rule, as CodeQL writes it and as GitHub
     * code scanning reads it, with a score in the band of each severity. An acceptance is an
     * accepted suppression whose location is the file recording it. SARIF has no field for an
     * expiry: [validate] refuses a SARIF scan declaring one rather than dropping it here.
     */
    private fun sarif(scan: ScanSpec): JsonNode = mapOf(
        "version" to "2.1.0",
        "\$schema" to "https://json.schemastore.org/sarif-2.1.0.json",
        "runs" to listOf(
            mapOf(
                "tool" to mapOf(
                    "driver" to mapOf(
                        "name" to scan.scanner,
                        "rules" to scan.findings.distinctBy { it.externalId }.map { finding ->
                            listOfNotNull(
                                "id" to finding.externalId,
                                "shortDescription" to mapOf("text" to finding.title),
                                finding.url?.let { "helpUri" to it },
                                SECURITY_SEVERITIES[finding.severity]?.let {
                                    "properties" to mapOf("security-severity" to it)
                                },
                            ).toMap()
                        },
                    ),
                ),
                "results" to scan.findings.map { finding ->
                    listOfNotNull(
                        "ruleId" to finding.externalId,
                        // Required by SARIF and never read by Yontrack, which is where a secret
                        // scanner puts the secret: the title is all it needs to say.
                        "message" to mapOf("text" to finding.title),
                        "locations" to listOf(
                            mapOf(
                                "physicalLocation" to mapOf(
                                    "artifactLocation" to mapOf("uri" to finding.location),
                                ),
                            ),
                        ),
                        finding.acceptance?.let { acceptance ->
                            "suppressions" to listOf(
                                mapOf(
                                    "kind" to "external",
                                    "status" to "accepted",
                                    "justification" to acceptance.statement,
                                    "location" to mapOf(
                                        "physicalLocation" to mapOf(
                                            "artifactLocation" to mapOf("uri" to acceptance.source),
                                        ),
                                    ),
                                ),
                            )
                        },
                    ).toMap()
                },
            ),
        ),
    ).asJson()

    /**
     * A score in the band of each severity by the thresholds of GitHub code scanning. UNKNOWN has
     * none: a rule without a score, and without a level, is read as UNKNOWN.
     */
    private val SECURITY_SEVERITIES = mapOf(
        FindingSeverity.CRITICAL to "9.8",
        FindingSeverity.HIGH to "7.5",
        FindingSeverity.MEDIUM to "5.3",
        FindingSeverity.LOW to "3.1",
    )
}
