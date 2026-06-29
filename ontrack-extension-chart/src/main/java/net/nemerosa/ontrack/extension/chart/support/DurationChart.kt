package net.nemerosa.ontrack.extension.chart.support

import net.nemerosa.ontrack.extension.chart.Chart
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import java.time.LocalDateTime


data class DurationChart(
    val categories: List<String>,
    val dates: List<String>,
    val data: DurationChartData,
): Chart {

    companion object {

        const val TYPE = "duration"

        fun compute(
            items: List<DurationChartItemData>,
            interval: Interval,
            period: String,
        ): DurationChart {
            // Period based formatting
            val intervalPeriod = parseIntervalPeriod(period)
            // Gets all the intervals for the given period
            val intervals = interval.split(intervalPeriod)
            // Chart
            return DurationChart(
                categories = listOf(
                    CATEGORY_MEAN,
                    CATEGORY_PERCENTILE_90,
                    CATEGORY_MAXIMUM,
                ),
                dates = intervals.map {
                    intervalPeriod.format(it.end)
                },
                data = computeData(items, intervals)
            )
        }

        private fun computeData(
            items: List<DurationChartItemData>,
            intervals: List<Interval>,
        ): DurationChartData {
            val points = intervals.map { interval ->
                computeDataPoint(items, interval)
            }
            return DurationChartData(
                mean = points.map { it.mean },
                percentile90 = points.map { it.percentile90 },
                maximum = points.map { it.maximum },
            )
        }

        private fun computeDataPoint(
            items: List<DurationChartItemData>,
            interval: Interval,
        ): DurationChartDataPoint {
            val sample = items.filter {
                it.timestamp in interval
            }.mapNotNull {
                it.value
            }
            val stats = DescriptiveStatistics()
            sample.forEach { stats.addValue(it) }

            return DurationChartDataPoint(
                mean = stats.mean,
                percentile90 = stats.getPercentile(90.0),
                maximum = stats.max,
            )
        }

        private const val CATEGORY_MEAN = "Mean"
        private const val CATEGORY_PERCENTILE_90 = "90th percentile"
        private const val CATEGORY_MAXIMUM = "Maximum"

    }

    data class DurationChartData(
        val mean: List<Double>,
        val percentile90: List<Double>,
        val maximum: List<Double>,
    )

    data class DurationChartDataPoint(
        val mean: Double,
        val percentile90: Double,
        val maximum: Double,
    )

}

data class DurationChartItemData(
    val timestamp: LocalDateTime,
    val value: Double?,
)