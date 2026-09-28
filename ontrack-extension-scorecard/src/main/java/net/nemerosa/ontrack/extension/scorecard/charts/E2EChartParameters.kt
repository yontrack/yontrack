package net.nemerosa.ontrack.extension.scorecard.charts

/**
 * Parameters of the end-to-end lead time chart.
 *
 * @property refPromotionId Promotion level to start from
 * @property samePromotion Target promotion level with the same name as the reference one
 * @property targetPromotionId Target promotion level
 * @property targetProject Project to go to
 * @property maxDepth Maximal depth of the build links to follow
 */
data class E2EChartParameters(
    val refPromotionId: Int,
    val samePromotion: Boolean,
    val targetPromotionId: Int?,
    val targetProject: String,
    val maxDepth: Int = 5,
)
