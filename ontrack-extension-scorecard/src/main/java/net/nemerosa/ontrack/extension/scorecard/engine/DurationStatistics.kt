package net.nemerosa.ontrack.extension.scorecard.engine

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

/**
 * Aggregation of the samples of a duration reading: the median goes in the value,
 * the rest in the details.
 */
data class DurationStatistics(
    val median: Double,
    val p90: Double,
    val mean: Double,
    val min: Double,
    val max: Double,
    val count: Int,
) {

    /**
     * Details of the reading. The median is its value.
     */
    val details: Map<String, Any?>
        get() = mapOf(
            "p90" to p90,
            "mean" to mean,
            "min" to min,
            "max" to max,
            "count" to count,
        )

    companion object {

        /**
         * Statistics of a list of durations, `null` if the list is empty.
         */
        fun of(values: Collection<Double>): DurationStatistics? =
            if (values.isEmpty()) {
                null
            } else {
                val stats = DescriptiveStatistics(values.toDoubleArray())
                DurationStatistics(
                    median = stats.getPercentile(50.0),
                    p90 = stats.getPercentile(90.0),
                    mean = stats.mean,
                    min = stats.min,
                    max = stats.max,
                    count = values.size,
                )
            }
    }
}
