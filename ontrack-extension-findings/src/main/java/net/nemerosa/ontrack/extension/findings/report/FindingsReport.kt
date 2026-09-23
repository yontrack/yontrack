package net.nemerosa.ontrack.extension.findings.report

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.model.json.schema.JsonSchemaString
import java.time.LocalDate

/**
 * Report of a scan in the neutral format of Yontrack, the `findings` format.
 *
 * These classes are the contract of the format: the JSON schema published on the Resources page
 * is generated from them. The format has no field for anything else, and a report carrying an
 * unknown field is rejected: that is how a secret value has nowhere to go.
 */
@APIDescription("Report of a security scan, in the neutral format of Yontrack.")
data class FindingsReport(
    @APIDescription("Name of the scanner which produced the report, as a free string (trivy, codeql, zap…). The `scanner` argument of the mutation takes precedence over it. One of the two is required.")
    val scanner: String? = null,
    @APIDescription("Kind of scan. The `kind` argument of the mutation takes precedence over it. One of the two is required.")
    val kind: FindingKind? = null,
    @APIDescription("Findings reported by the scan. May be empty.")
    val findings: List<FindingsReportEntry>,
)

/**
 * One finding in a report.
 */
@APIDescription("One finding reported by a scan.")
data class FindingsReportEntry(
    @APIDescription("Identifier given by the scanner: a CVE, a rule ID, an alert number…")
    val externalId: String,
    @APIDescription("Where the finding is: a purl for a dependency (its version and qualifiers are dropped, the version going to the observation), a path without line for code, the provider's alert number for a secret, empty for a DAST rule.")
    val location: String,
    @APIDescription("Severity of the finding. UNKNOWN is counted and shown, but never trips a threshold.")
    val severity: FindingSeverity,
    @APIDescription("Severity as the scanner gave it, for provenance.")
    val rawSeverity: String? = null,
    @APIDescription("Short description of the finding.")
    val title: String,
    @APIDescription("Link to more information about the finding.")
    val url: String? = null,
    @APIDescription("Version of the component fixing the finding, if any.")
    val fixedVersion: String? = null,
    @APIDescription("Version of the component in which the finding was observed. Defaults to the version of a purl location.")
    val installedVersion: String? = null,
    @APIDescription("Acceptance of the finding, when it is tolerated.")
    val acceptance: FindingsReportAcceptance? = null,
)

/**
 * Acceptance of a finding in a report.
 */
@APIDescription("A decision recorded outside Yontrack that a finding is tolerated, possibly until an expiry.")
data class FindingsReportAcceptance(
    @APIDescription("Why the finding is tolerated.")
    val statement: String,
    @APIDescription("Last day the acceptance holds, as an ISO date (yyyy-MM-dd). No expiry when absent.")
    @JsonSchemaString
    val expiresAt: LocalDate? = null,
    @APIDescription("Where the decision is recorded, for example a suppressions file.")
    val source: String,
)

/**
 * Report as read by a [FindingsReportParser], whatever its format, its scanner and its kind
 * resolved.
 *
 * @property scanner Name of the scanner
 * @property kind Kind of scan
 * @property findings Findings of the report, as given by the scanner (not normalised, not
 * de-duplicated)
 */
data class ParsedFindingsReport(
    val scanner: String,
    val kind: FindingKind,
    val findings: List<FindingsReportEntry>,
)
