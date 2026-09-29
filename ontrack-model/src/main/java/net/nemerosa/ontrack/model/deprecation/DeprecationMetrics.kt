package net.nemerosa.ontrack.model.deprecation

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.common.doc.MetricsDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterTag
import net.nemerosa.ontrack.common.doc.MetricsMeterType

@Suppress("ConstPropertyName")
@MetricsDocumentation
@APIName("Deprecation metrics")
@APIDescription("Usage of the deprecated external items, which the next major version removes.")
object DeprecationMetrics {

    @APIDescription("Number of times a deprecated item has been used. Exported to Prometheus as `ontrack_deprecated_usage_total`.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("surface", "External contract used: graphql, rest, config, casc, env, templating, settings, property or ci-config"),
            MetricsMeterTag("item", "Deprecated item which was used"),
        ]
    )
    const val usage = "ontrack.deprecated.usage"

}
