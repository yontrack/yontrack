package net.nemerosa.ontrack.graphql.limits

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.common.doc.MetricsDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterTag
import net.nemerosa.ontrack.common.doc.MetricsMeterType

@Suppress("ConstPropertyName")
@MetricsDocumentation
@APIName("GraphQL limits metrics")
@APIDescription("GraphQL queries going over the limits on their cost, set by `ontrack.config.graphql.limits`.")
object GraphQLLimitsMetrics {

    @APIDescription("Number of GraphQL queries which went over one of the limits on their cost. Exported to Prometheus as `ontrack_graphql_limits_exceeded_total`.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag("limit", "Limit the query went over: aliases, directives, depth or complexity"),
            MetricsMeterTag("mode", "enforce when the query was rejected, warn when it ran all the same"),
        ]
    )
    const val exceeded = "ontrack.graphql.limits.exceeded"

}
