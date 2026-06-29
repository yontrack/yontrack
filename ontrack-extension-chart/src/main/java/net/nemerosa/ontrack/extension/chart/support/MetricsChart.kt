package net.nemerosa.ontrack.extension.chart.support

import net.nemerosa.ontrack.extension.chart.Chart
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import java.math.RoundingMode
import java.time.LocalDateTime

class MetricsChart(
        @Suppress("unused") val dates: List<String>,
        @Suppress("unused") val metricNames: List<String>,
        @Suppress("unused") val metricColors: List<String>?,
        @Suppress("unused") val metricValues: List<Map<String, Double>>,
) : Chart {

    companion object {
        const val TYPE = "metrics"

        fun compute(
                names: List<String>?,
                colors: List<String>?,
                items: List<MetricsChartItemData>,
                interval: Interval,
                period: String,
        ): MetricsChart {
            // Period based formatting
            val intervalPeriod = parseIntervalPeriod(period)
            // Gets all the intervals for the given period
            val intervals = interval.split(intervalPeriod)

            // Group the metrics per interval and take the average
            val metricNames = mutableSetOf<String>()
            val metricValues = intervals.map { internalInterval ->
                val itemsInInterval = items.filter { item ->
                    item.timestamp in internalInterval
                }.map { it.metrics }
                // If no item in the interval, does not return any data
                if (itemsInInterval.isEmpty()) {
                    emptyMap()
                }
                // Values per metrics name
                else {
                    val metricsValues = mutableMapOf<String, MutableList<Double>>()
                    itemsInInterval.forEach { metrics ->
                        metrics.forEach { (metric, value) ->
                            metricNames.add(metric)
                            metricsValues
                                    .getOrPut(metric) { mutableListOf() }
                                    .add(value)
                        }
                    }
                    // Getting the average per metric
                    metricsValues.mapValues { (_, values) ->
                        val stats = DescriptiveStatistics()
                        values.forEach { stats.addValue(it) }
                        stats.mean.toBigDecimal().setScale(1, RoundingMode.HALF_UP).toDouble()
                    }
                }
            }

            // Chart
            return MetricsChart(
                    dates = intervals.map {
                        intervalPeriod.format(it.end)
                    },
                    metricNames = names ?: metricNames.sorted(),
                    metricColors = colors,
                    metricValues = metricValues,
            )
        }
    }

}

data class MetricsChartItemData(
        val timestamp: LocalDateTime,
        val metrics: Map<String, Double>,
)
