package net.nemerosa.ontrack.extension.scorecard.metrics

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.common.doc.MetricsDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterField
import net.nemerosa.ontrack.common.doc.MetricsMeterDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterTag
import net.nemerosa.ontrack.common.doc.MetricsMeterType
import net.nemerosa.ontrack.model.docs.DocumentationIgnore

/**
 * Metrics of the delivery scorecard: the computation of its readings, and the readings themselves.
 */
@Suppress("ConstPropertyName")
@MetricsDocumentation
@APIName("Delivery scorecard metrics")
@APIDescription("Metrics for the readings of the delivery scorecard and their computation.")
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

    @APIDescription(
        "A reading of a project, for one set, exported to the metrics backends (InfluxDB, Elastic) " +
                "each time the readings of the project are computed, by the daily job or a recompute. " +
                "Its timestamp is the time the reading was computed. " +
                "The re-export of all the metrics replays the stored daily snapshots."
    )
    @MetricsMeterDocumentation(
        type = MetricsMeterType.EXPORTED,
        tags = [
            MetricsMeterTag(Tags.ESTATE, "Name of the estate of the set, `-` for the set with no estate."),
            MetricsMeterTag(Tags.PROJECT, "Name of the project."),
            MetricsMeterTag(Tags.READING, "Key of the reading, like `delivery.leadTime`."),
            MetricsMeterTag(Tags.BASIS, "What the value rests on: `MEASURED`, `ESTIMATED` or `UNKNOWN`."),
        ],
        fields = [
            MetricsMeterField(
                Fields.VALUE,
                "Value of the reading: seconds for a duration, per week for a frequency, " +
                        "0 to 100 for a rate. Absent when the basis is `UNKNOWN`."
            ),
        ]
    )
    const val reading = "ontrack_reading"

    @DocumentationIgnore
    object Tags {
        const val ESTATE = "estate"
        const val PROJECT = "project"
        const val READING = "reading"
        const val BASIS = "basis"
    }

    @DocumentationIgnore
    object Fields {
        const val VALUE = "value"
    }
}
