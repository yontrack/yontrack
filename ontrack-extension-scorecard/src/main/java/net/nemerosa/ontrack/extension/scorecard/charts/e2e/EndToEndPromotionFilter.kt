package net.nemerosa.ontrack.extension.scorecard.charts.e2e

import java.time.LocalDateTime

/**
 * Filter on the end-to-end promotion records.
 *
 * @property minDepth Minimal depth of the build links from the reference build (1 for the build itself)
 * @property maxDepth Maximal depth of the build links from the reference build
 * @property afterTime Reference builds created at or after this time
 * @property beforeTime Reference builds created at or before this time
 * @property samePromotion Target promotion level with the same name as the reference one
 * @property promotionId ID of the reference promotion level
 * @property targetPromotionId ID of the target promotion level
 * @property targetProject Name of the target project
 */
data class EndToEndPromotionFilter(
    val minDepth: Int = 1,
    val maxDepth: Int = 50,
    val afterTime: LocalDateTime? = null,
    val beforeTime: LocalDateTime? = null,
    val samePromotion: Boolean = true,
    val promotionId: Int? = null,
    val targetPromotionId: Int? = null,
    val targetProject: String? = null,
)