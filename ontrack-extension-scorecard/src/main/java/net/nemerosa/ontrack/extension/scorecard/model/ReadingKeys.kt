package net.nemerosa.ontrack.extension.scorecard.model

/**
 * Keys of the readings of the catalogue, in the order a scorecard lists them.
 */
object ReadingKeys {

    /**
     * Build creation to its first promotion at the marker. Median, in seconds.
     */
    const val DELIVERY_LEAD_TIME = "delivery.leadTime"

    /**
     * Promotions at the marker, per week.
     */
    const val DELIVERY_FREQUENCY = "delivery.frequency"

    /**
     * Share of the builds which reached the marker, the builds in flight left out. Percentage, 0 to 100.
     */
    const val DELIVERY_SUCCESS_RATE = "delivery.successRate"

    /**
     * Time to restore: first unpromoted build after a promoted one to the next promotion at the marker.
     * Median, in seconds.
     */
    const val DELIVERY_MTTR = "delivery.mttr"

    /**
     * Share of the builds with a test run whose latest run passed on every test stamp. Percentage, 0 to 100.
     */
    const val QUALITY_TEST_PASS_RATE = "quality.testPassRate"

    /**
     * Share of the builds with a test run where a test stamp has a `FAILED` run followed by a `PASSED` run.
     * Percentage, 0 to 100.
     */
    const val QUALITY_TEST_FLAKINESS = "quality.testFlakiness"

    /**
     * Rung of the security scans on the ladder: 0 none, 1 reported, 2 covered, 3 gating.
     */
    const val SECURITY_MATURITY = "security.maturity"

    /**
     * Catalogue order
     */
    val ORDER: List<String> = listOf(
        DELIVERY_LEAD_TIME,
        DELIVERY_FREQUENCY,
        DELIVERY_SUCCESS_RATE,
        DELIVERY_MTTR,
        QUALITY_TEST_PASS_RATE,
        QUALITY_TEST_FLAKINESS,
        SECURITY_MATURITY,
    )

    /**
     * Which way each reading of the catalogue is better, and so how a target judges it
     */
    private val DIRECTIONS: Map<String, ReadingDirection> = mapOf(
        DELIVERY_LEAD_TIME to ReadingDirection.LOWER_IS_BETTER,
        DELIVERY_FREQUENCY to ReadingDirection.HIGHER_IS_BETTER,
        DELIVERY_SUCCESS_RATE to ReadingDirection.HIGHER_IS_BETTER,
        DELIVERY_MTTR to ReadingDirection.LOWER_IS_BETTER,
        QUALITY_TEST_PASS_RATE to ReadingDirection.HIGHER_IS_BETTER,
        QUALITY_TEST_FLAKINESS to ReadingDirection.LOWER_IS_BETTER,
        SECURITY_MATURITY to ReadingDirection.HIGHER_IS_BETTER,
    )

    /**
     * Which way a reading is better, `null` for a key out of the catalogue
     */
    fun direction(key: String): ReadingDirection? = DIRECTIONS[key]

    /**
     * Rank of a key in the catalogue, the keys out of the catalogue last
     */
    fun rank(key: String): Int = ORDER.indexOf(key).takeIf { it >= 0 } ?: Int.MAX_VALUE
}
