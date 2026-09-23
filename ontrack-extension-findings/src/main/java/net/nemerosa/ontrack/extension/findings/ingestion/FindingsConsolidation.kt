package net.nemerosa.ontrack.extension.findings.ingestion

import net.nemerosa.ontrack.extension.findings.location.FindingLocations
import net.nemerosa.ontrack.extension.findings.model.FindingAcceptance
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.report.FindingsReportEntry
import net.nemerosa.ontrack.extension.findings.validation.FindingsValidationDataTypeData
import net.nemerosa.ontrack.extension.general.validation.CHML
import java.time.LocalDate

/**
 * One finding of a report, once its location is normalised and its duplicates merged.
 *
 * @property externalId Identifier given by the scanner
 * @property location Normalised location
 * @property severity Severity
 * @property rawSeverity Severity as the scanner gave it
 * @property title Short description
 * @property url Link to more information
 * @property installedVersion Version in which the finding was observed
 * @property fixedVersion Version fixing the finding
 * @property acceptance Acceptance, as reported, even if expired
 * @property accepted Whether the acceptance holds on the day of the scan
 */
data class ConsolidatedFinding(
    val externalId: String,
    val location: String,
    val severity: FindingSeverity,
    val rawSeverity: String?,
    val title: String,
    val url: String?,
    val installedVersion: String?,
    val fixedVersion: String?,
    val acceptance: FindingAcceptance?,
    val accepted: Boolean,
)

/**
 * From the entries of a report to the findings of one scan, and to their counts.
 */
object FindingsConsolidation {

    /**
     * Normalises the locations of the entries and merges the entries having the same key, since a
     * scan observes a finding once.
     *
     * Two entries have the same key when they have the same external ID and the same normalised
     * location — for example, one CVE reported on two versions of one package. The entry kept is
     * the one counting the most: not accepted before accepted, then the most severe, then the
     * first one of the report.
     *
     * @param entries Entries of the report
     * @param date Day of the scan, against which the expiry of the acceptances is evaluated
     * @return Findings of the scan, in the order of the report
     */
    fun consolidate(entries: List<FindingsReportEntry>, date: LocalDate): List<ConsolidatedFinding> =
        entries
            .map { consolidate(it, date) }
            .groupBy { it.externalId to it.location }
            .values
            .map { duplicates ->
                duplicates.minWith(
                    compareBy<ConsolidatedFinding> { it.accepted }.thenBy { it.severity.ordinal }
                )
            }

    private fun consolidate(entry: FindingsReportEntry, date: LocalDate): ConsolidatedFinding {
        val location = FindingLocations.normalise(entry.location)
        val acceptance = entry.acceptance?.let {
            FindingAcceptance(
                statement = it.statement,
                expiresAt = it.expiresAt,
                source = it.source,
            )
        }
        return ConsolidatedFinding(
            externalId = entry.externalId,
            location = location.location,
            severity = entry.severity,
            rawSeverity = entry.rawSeverity,
            title = entry.title,
            url = entry.url,
            installedVersion = entry.installedVersion ?: location.version,
            fixedVersion = entry.fixedVersion,
            acceptance = acceptance,
            accepted = acceptance != null && acceptance.isEffectiveOn(date),
        )
    }

    /**
     * Counts of the findings of a scan: the accepted ones apart, the others per severity.
     */
    fun counts(findings: List<ConsolidatedFinding>): FindingsValidationDataTypeData {
        val notAccepted = findings.filterNot { it.accepted }
        return FindingsValidationDataTypeData(
            levels = CHML.entries.associateWith { level ->
                notAccepted.count { it.severity.name == level.name }
            },
            unknown = notAccepted.count { it.severity == FindingSeverity.UNKNOWN },
            accepted = findings.count { it.accepted },
        )
    }
}
