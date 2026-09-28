package net.nemerosa.ontrack.extension.scorecard.charts.e2e

import java.time.LocalDateTime

data class EndToEndPromotionNode(
    val project: String,
    val branch: String,
    val build: String,
    val buildCreation: LocalDateTime,
    val promotionId: Int?,
    val promotion: String?,
    val promotionCreation: LocalDateTime?,
)