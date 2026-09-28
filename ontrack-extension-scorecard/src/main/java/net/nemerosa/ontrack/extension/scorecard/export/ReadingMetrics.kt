package net.nemerosa.ontrack.extension.scorecard.export

import net.nemerosa.ontrack.extension.scorecard.metrics.ScorecardMetrics
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.model.metrics.Metric

/**
 * A reading as the exported metric [ontrack_reading][ScorecardMetrics.reading].
 */
object ReadingMetrics {

    /**
     * The metric of a reading, timestamped with the time it was computed. An unknown reading has
     * no value, hence no field.
     *
     * @param estate Tag of the set, see [net.nemerosa.ontrack.extension.scorecard.model.ReadingSet.tag]
     * @param project Name of the project
     * @param reading Reading to export
     */
    fun metric(estate: String, project: String, reading: Reading) = Metric(
        metric = ScorecardMetrics.reading,
        tags = mapOf(
            ScorecardMetrics.Tags.ESTATE to estate,
            ScorecardMetrics.Tags.PROJECT to project,
            ScorecardMetrics.Tags.READING to reading.key,
            ScorecardMetrics.Tags.BASIS to reading.basis.name,
        ),
        fields = reading.value?.let { mapOf(ScorecardMetrics.Fields.VALUE to it) } ?: emptyMap<String, Double>(),
        timestamp = reading.computedAt,
    )
}
