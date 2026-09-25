package net.nemerosa.ontrack.service.search

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.common.doc.MetricsDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterTag
import net.nemerosa.ontrack.common.doc.MetricsMeterType

@Suppress("ConstPropertyName")
@MetricsDocumentation
@APIName("Search index metrics")
@APIDescription("Metrics related to the writing of the search documents into Postgres.")
object SearchIndexMetrics {

    const val METRIC_TYPE = "type"

    /**
     * Prefix for all metric names
     */
    private const val prefix = "ontrack_search"

    @APIDescription("Number of search documents which could not be written. The entity being changed is not affected, and the next rebuild of the type repairs the documents.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag(METRIC_TYPE, "Search result type of the document"),
        ]
    )
    const val indexErrors = "${prefix}_index_errors"

    @APIDescription("Duration of the full rebuild of the search documents of a type.")
    @MetricsMeterDocumentation(
        type = MetricsMeterType.TIMER,
        tags = [
            MetricsMeterTag(METRIC_TYPE, "Search result type being rebuilt"),
        ]
    )
    const val rebuild = "${prefix}_rebuild"
}
