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
}
