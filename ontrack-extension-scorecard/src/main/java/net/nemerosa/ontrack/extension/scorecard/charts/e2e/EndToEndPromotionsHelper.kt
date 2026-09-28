package net.nemerosa.ontrack.extension.scorecard.charts.e2e

/**
 * Gets raw data about end-to-end promotions, following any build link in Ontrack.
 */
interface EndToEndPromotionsHelper {

    fun forEachEndToEndPromotionRecord(
        filter: EndToEndPromotionFilter = EndToEndPromotionFilter(),
        code: (record: EndToEndPromotionRecord) -> Unit,
    )

}