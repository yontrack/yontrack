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
     * Catalogue order
     */
    val ORDER: List<String> = listOf(
        DELIVERY_LEAD_TIME,
        DELIVERY_FREQUENCY,
        DELIVERY_SUCCESS_RATE,
        DELIVERY_MTTR,
        QUALITY_TEST_PASS_RATE,
        QUALITY_TEST_FLAKINESS,
    )

    /**
     * Rank of a key in the catalogue, the keys out of the catalogue last
     */
    fun rank(key: String): Int = ORDER.indexOf(key).takeIf { it >= 0 } ?: Int.MAX_VALUE
}
