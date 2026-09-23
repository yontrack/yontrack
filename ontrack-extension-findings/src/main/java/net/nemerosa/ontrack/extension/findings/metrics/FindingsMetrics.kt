package net.nemerosa.ontrack.extension.findings.metrics

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.common.doc.MetricsDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterTag
import net.nemerosa.ontrack.common.doc.MetricsMeterType
import net.nemerosa.ontrack.model.docs.DocumentationIgnore

/**
 * Metrics of the security findings.
 *
 * The ingestion of a report is synchronous: these metrics keep it under watch, and tell when
 * the parsing must move to a queue — see `doc/dev-guide/findings-ingestion.md`.
 *
 * Only the `format` tag is used, and only for the formats Yontrack knows: the format of a report
 * is given by the client, and an unknown one is rejected before anything is measured.
 */
@Suppress("ConstPropertyName")
@MetricsDocumentation
@APIName("Findings metrics")
@APIDescription("Metrics for the ingestion of the reports of security scans.")
object FindingsMetrics {

    @APIDescription(
        "Duration of the ingestion of a report of security scan, from its reading to the writing of its findings, " +
                "their observations and their exposure. Only the reports which are ingested are measured. " +
                "Published as a histogram, with a bucket at 5 seconds, for its percentiles to be computed."
    )
    @MetricsMeterDocumentation(
        type = MetricsMeterType.TIMER,
        tags = [
            MetricsMeterTag(Tags.FORMAT, "Format of the report: `findings`, `sarif` or `trivy`."),
        ]
    )
    const val ingestion = "ontrack_findings_ingestion"

    @APIDescription(
        "Number of findings in an ingested report, after the entries for the same finding are merged. " +
                "Published as a histogram."
    )
    @MetricsMeterDocumentation(
        type = MetricsMeterType.DISTRIBUTION_SUMMARY,
        tags = [
            MetricsMeterTag(Tags.FORMAT, "Format of the report: `findings`, `sarif` or `trivy`."),
        ]
    )
    const val ingestionFindings = "ontrack_findings_ingestion_findings"

    @DocumentationIgnore
    object Tags {
        const val FORMAT = "format"
    }
}
