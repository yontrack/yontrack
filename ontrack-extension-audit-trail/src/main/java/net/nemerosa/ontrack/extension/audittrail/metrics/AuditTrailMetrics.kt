package net.nemerosa.ontrack.extension.audittrail.metrics

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.common.doc.MetricsDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterTag
import net.nemerosa.ontrack.common.doc.MetricsMeterType
import net.nemerosa.ontrack.model.docs.DocumentationIgnore

/**
 * Metrics of the audit trail.
 *
 * An entry is appended in the transaction of the change it records, under a lock on the trail of
 * its build: the append timer keeps the cost it adds to every change under watch.
 */
@Suppress("ConstPropertyName")
@MetricsDocumentation
@APIName("Audit trail metrics")
@APIDescription("Metrics for the trails of the builds.")
object AuditTrailMetrics {

    @APIDescription(
        "Duration of the append of an entry to the trail of a build, lock on the trail included. " +
                "Only the appends made while the licence is on are measured."
    )
    @MetricsMeterDocumentation(
        type = MetricsMeterType.TIMER,
        tags = [
            MetricsMeterTag(Tags.TYPE, "Type of the entry, such as `build.created`."),
        ]
    )
    const val append = "ontrack_audit_trail_append"

    @APIDescription(
        "Number of trails found tampered with by the daily verification — a broken chain or an invalid " +
                "endorsement. A trail is verified again, and counted again, each time it gains entries."
    )
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
    )
    const val verificationFailures = "ontrack_audit_trail_verification_failures"

    @DocumentationIgnore
    object Tags {
        const val TYPE = "type"
    }
}
