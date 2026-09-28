package net.nemerosa.ontrack.extension.scorecard.metrics

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.common.doc.MetricsDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterTag
import net.nemerosa.ontrack.common.doc.MetricsMeterType
import net.nemerosa.ontrack.model.docs.DocumentationIgnore

/**
 * Metrics of the computation of the readings of the delivery scorecard.
 */
@Suppress("ConstPropertyName")
@MetricsDocumentation
@APIName("Delivery scorecard metrics")
@APIDescription("Metrics for the computation of the readings of the delivery scorecard.")
object ScorecardMetrics {

    @APIDescription(
        "Duration of the computation of the readings of one project for one set. " +
                "A computation which fails is measured as well."
    )
    @MetricsMeterDocumentation(
        type = MetricsMeterType.TIMER,
        tags = [
            MetricsMeterTag(Tags.ESTATE, "Name of the estate of the set, `-` for the set with no estate."),
        ]
    )
    const val computation = "ontrack_readings_computation"

    @APIDescription(
        "Number of computations of the readings of one project for one set which have failed. " +
                "A project whose computation fails gets no reading that day."
    )
    @MetricsMeterDocumentation(
        type = MetricsMeterType.COUNT,
        tags = [
            MetricsMeterTag(Tags.ESTATE, "Name of the estate of the set, `-` for the set with no estate."),
        ]
    )
    const val errors = "ontrack_readings_errors"

    @DocumentationIgnore
    object Tags {
        const val ESTATE = "estate"
    }
}
