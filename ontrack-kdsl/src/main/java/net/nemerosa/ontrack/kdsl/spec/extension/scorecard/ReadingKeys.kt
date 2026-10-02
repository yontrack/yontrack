package net.nemerosa.ontrack.kdsl.spec.extension.scorecard

/**
 * Keys of the readings of the delivery scorecard, as the API and the estates name them.
 *
 * Durations are in seconds, frequencies per week and rates from 0 to 100.
 */
object ReadingKeys {

    /**
     * Median time from the creation of a build to the marker, in seconds. Lower is better.
     */
    const val DELIVERY_LEAD_TIME = "delivery.leadTime"

    /**
     * Number of times the marker is reached, per week. Higher is better.
     */
    const val DELIVERY_FREQUENCY = "delivery.frequency"

    /**
     * Share of the builds (promotion marker) or deployments (environment marker) reaching the
     * marker, from 0 to 100. Higher is better.
     */
    const val DELIVERY_SUCCESS_RATE = "delivery.successRate"

    /**
     * Median time to restore the path to the marker, in seconds. Lower is better.
     */
    const val DELIVERY_MTTR = "delivery.mttr"

    /**
     * Share of the builds whose latest run on every test stamp passed, from 0 to 100. Higher is better.
     */
    const val QUALITY_TEST_PASS_RATE = "quality.testPassRate"

    /**
     * Share of the builds with a failed test run followed by a passed one, from 0 to 100. Lower is better.
     */
    const val QUALITY_TEST_FLAKINESS = "quality.testFlakiness"

    /**
     * Rung of the security scans: 0 none, 1 reported, 2 covered, 3 gating. Higher is better.
     */
    const val SECURITY_MATURITY = "security.maturity"

    /**
     * Median time from the first observation of a CRITICAL or HIGH finding to its resolution in the
     * project, in seconds, for the findings resolved in the window. Lower is better.
     */
    const val SECURITY_REMEDIATION_TIME = "security.remediationTime"

    /**
     * Open CRITICAL and HIGH findings older than the remediation targets of the estate. Lower is better.
     */
    const val SECURITY_OVERDUE = "security.overdue"
}
