package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.environments.Slot

/**
 * Sample functions under an environment marker: the deployments of one slot.
 *
 * Every function returns the samples which reached the marker in the interval, the start of the
 * interval included, its end excluded. A deployment reaches the marker at its `end`, when it is
 * done. A cancelled deployment is never a sample.
 */
interface EnvironmentSamples {

    /**
     * Lead times: from the creation of a build to the end of its **first** done deployment in the
     * slot. A build is kept when this first deployment ended in the interval: a redeployment does
     * not reset it, nor count again.
     */
    fun leadTimes(slot: Slot, interval: Interval): List<DurationSample>

    /**
     * Deployments done in the slot, ended in the interval.
     */
    fun deployments(slot: Slot, interval: Interval): List<EventSample>

    /**
     * Deployments done or failed in the slot, ended in the interval.
     */
    fun outcomes(slot: Slot, interval: Interval): List<DeploymentSample>

    /**
     * Outages of the slot, as [DeploymentOutages.of] reads them at the end of the interval: those
     * restored in the interval, and the one still going on at its end.
     *
     * The deployments before the start of the interval are read too, since an outage may start
     * before it.
     */
    fun outages(slot: Slot, interval: Interval): List<OutageSample>
}
