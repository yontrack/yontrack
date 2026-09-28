package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.extension.chart.support.Interval
import java.time.LocalDateTime

/**
 * Outages of branches, from the promotion state of their builds.
 *
 * On each branch, the builds are taken in the order of their creation (their ID breaking the ties).
 * An outage starts with the **first** unpromoted build following a promoted one, and ends with the
 * promotion of the next promoted build. For B1 promoted, B2, B3 and B4 unpromoted, and B5
 * promoted, the outage goes from the creation of B2 to the promotion of B5.
 *
 * Unpromoted builds before the first promoted build of a branch are not an outage: nothing was
 * promoted before them to be restored.
 */
object Outages {

    /**
     * Outages of the branches of the builds, as seen at the end of the interval.
     *
     * @param builds Builds of the branches, created before the end of the interval — the builds
     * before the start of the interval included, since an outage may start before it
     * @param interval Interval the outages are read for
     * @return The outages restored in the interval (start included, end excluded), and the outages
     * still going on at its end, ordered by start
     */
    fun of(builds: List<BuildSample>, interval: Interval): List<OutageSample> =
        builds
            .filter { it.creation < interval.end }
            .groupBy { it.branchId }
            .flatMap { (branchId, branchBuilds) -> branchOutages(branchId, branchBuilds, interval.end) }
            .filter { it.restored == null || it.restored in interval }
            .sortedWith(compareBy({ it.start }, { it.branchId }))

    private fun branchOutages(branchId: Int, builds: List<BuildSample>, end: LocalDateTime): List<OutageSample> {
        val outages = mutableListOf<OutageSample>()
        var afterPromoted = false
        var start: LocalDateTime? = null
        builds.sortedWith(compareBy({ it.creation }, { it.buildId })).forEach { build ->
            // A promotion after the end of the interval is not known yet
            val promotion = build.promotion?.takeIf { it < end }
            if (promotion != null) {
                if (start != null) {
                    outages += OutageSample(branchId = branchId, start = start, restored = promotion)
                    start = null
                }
                afterPromoted = true
            } else if (afterPromoted && start == null) {
                start = build.creation
            }
        }
        start?.let { outages += OutageSample(branchId = branchId, start = it, restored = null) }
        return outages
    }
}
