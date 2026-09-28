package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.extension.chart.support.Interval

/**
 * Outages of a slot, from its deployments: the time it takes to restore the deployment path — not
 * an incident's time to restore, which Yontrack cannot see.
 *
 * The deployments are taken in the order of their end (their number breaking the ties). An outage
 * starts when a deployment fails and ends when the next one is done. Consecutive failures make one
 * outage, from the first of them.
 */
object DeploymentOutages {

    /**
     * Outages of the slot of the deployments, as seen at the end of the interval.
     *
     * @param deployments Deployments of the slot, done or failed — the deployments before the start
     * of the interval included, since an outage may start before it
     * @param interval Interval the outages are read for
     * @return The outages restored in the interval (start included, end excluded), and the outage
     * still going on at its end, ordered by start
     */
    fun of(deployments: List<DeploymentSample>, interval: Interval): List<OutageSample> {
        val outages = mutableListOf<OutageSample>()
        var failure: DeploymentSample? = null
        deployments
            .filter { it.end < interval.end }
            .sortedWith(compareBy({ it.end }, { it.number }))
            .forEach { deployment ->
                val started = failure
                if (deployment.failed) {
                    if (started == null) {
                        failure = deployment
                    }
                } else if (started != null) {
                    outages += OutageSample(branchId = started.branchId, start = started.end, restored = deployment.end)
                    failure = null
                }
            }
        failure?.let { outages += OutageSample(branchId = it.branchId, start = it.end, restored = null) }
        return outages.filter { it.restored == null || it.restored in interval }
    }
}
