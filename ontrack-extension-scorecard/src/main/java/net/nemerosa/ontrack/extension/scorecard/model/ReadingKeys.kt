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
     * Catalogue order
     */
    val ORDER: List<String> = listOf(
        DELIVERY_LEAD_TIME,
        DELIVERY_FREQUENCY,
    )

    /**
     * Rank of a key in the catalogue, the keys out of the catalogue last
     */
    fun rank(key: String): Int = ORDER.indexOf(key).takeIf { it >= 0 } ?: Int.MAX_VALUE
}
