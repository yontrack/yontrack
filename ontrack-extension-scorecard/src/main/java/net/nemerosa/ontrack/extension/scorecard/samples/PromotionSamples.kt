package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.model.structure.PromotionLevel

/**
 * Sample functions under a promotion marker, shared by the readings and the promotion-level charts,
 * so that a chart and the reading beside it cannot disagree.
 *
 * Every function takes the promotion levels the samples are read up to (at most one per branch) and
 * returns the samples which reached one of them in the interval, the start of the interval
 * included, its end excluded.
 */
interface PromotionSamples {

    /**
     * Lead times: from the creation of a build to its **first** promotion run at the level.
     * A build is kept when this first run is in the interval: a later promotion to the same level
     * does not count again.
     */
    fun leadTimes(levels: Collection<PromotionLevel>, interval: Interval): List<DurationSample>

    /**
     * Promotions: every promotion run at the levels whose creation is in the interval.
     */
    fun promotions(levels: Collection<PromotionLevel>, interval: Interval): List<EventSample>

    /**
     * Builds: every build created in the interval on the branches of the levels, with its first
     * promotion run at the level of its branch if it has one before the end of the interval.
     *
     * The success rate is read on these builds, once the builds [in flight][InFlight] are left out.
     */
    fun builds(levels: Collection<PromotionLevel>, interval: Interval): List<BuildSample>

    /**
     * Outages of the branches of the levels, as [Outages.of] reads them at the end of the interval:
     * those restored in the interval, and those still going on at its end.
     *
     * The builds before the start of the interval are read too, since an outage may start before it.
     */
    fun outages(levels: Collection<PromotionLevel>, interval: Interval): List<OutageSample>
}
