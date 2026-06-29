package net.nemerosa.ontrack.extension.chart.support

import net.nemerosa.ontrack.extension.chart.Chart
import java.time.LocalDateTime

data class CountChart(
    val dates: List<String>,
    val data: List<Double>,
): Chart {

    companion object {

        const val TYPE = "count"

        fun compute(items: List<CountChartItemData>, interval: Interval, period: String): CountChart {
            // Period based formatting
            val intervalPeriod = parseIntervalPeriod(period)
            // Gets all the intervals for the given period
            val intervals = interval.split(intervalPeriod)
            // Chart
            return CountChart(
                dates = intervals.map {
                    intervalPeriod.format(it.end)
                },
                data = computeData(items, intervals)
            )
        }

        private fun computeData(items: List<CountChartItemData>, intervals: List<Interval>) =
            intervals.map { interval ->
                computeDataPoint(items, interval)
            }

        private fun computeDataPoint(items: List<CountChartItemData>, interval: Interval): Double = items.count {
            it.timestamp in interval
        }.toDouble()

    }

}

data class CountChartItemData(
    val timestamp: LocalDateTime,
)