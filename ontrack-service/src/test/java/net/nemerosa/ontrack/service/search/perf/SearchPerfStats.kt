package net.nemerosa.ontrack.service.search.perf

import kotlin.math.ceil

/**
 * Statistics of the latencies.
 */
object SearchPerfStats {

    /**
     * Summary of the samples of a scenario, in milliseconds.
     */
    data class Summary(
        val samples: Int,
        val p50: Double,
        val p95: Double,
        val max: Double,
    )

    /**
     * Percentile of samples, by the nearest rank: the smallest sample such that at least [p]
     * percent of the samples are lower or equal to it.
     */
    fun percentile(samples: List<Double>, p: Double): Double {
        require(samples.isNotEmpty()) { "No sample" }
        val sorted = samples.sorted()
        val rank = ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    fun summary(samples: List<Double>) = Summary(
        samples = samples.size,
        p50 = percentile(samples, 50.0),
        p95 = percentile(samples, 95.0),
        max = samples.max(),
    )
}
