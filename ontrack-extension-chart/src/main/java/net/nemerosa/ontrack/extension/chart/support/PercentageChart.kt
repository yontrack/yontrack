package net.nemerosa.ontrack.extension.chart.support

import net.nemerosa.ontrack.extension.chart.Chart
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import java.time.LocalDateTime

data class PercentageChart(
    val dates: List<String>,
    val data: List<Double>,
): Chart {

    companion object {

        fun compute(items: List<PercentageChartItemData>, interval: Interval, period: String): PercentageChart {
            // Period based formatting
            val intervalPeriod = parseIntervalPeriod(period)
            // Gets all the intervals for the given period
            val intervals = interval.split(intervalPeriod)
            // Chart
            return PercentageChart(
                dates = intervals.map {
                    intervalPeriod.format(it.end)
                },
                data = computeData(items, intervals)
            )
        }

        private fun computeData(items: List<PercentageChartItemData>, intervals: List<Interval>) =
            intervals.map { interval ->
                computeDataPoint(items, interval)
            }

        private fun computeDataPoint(items: List<PercentageChartItemData>, interval: Interval): Double {
            val sample = items.filter {
                it.timestamp in interval
            }.mapNotNull {
                it.value
            }
            val stats = DescriptiveStatistics()
            sample.forEach { stats.addValue(it) }

            return stats.mean
        }

    }

}

data class PercentageChartItemData(
    val timestamp: LocalDateTime,
    val value: Double?,
)