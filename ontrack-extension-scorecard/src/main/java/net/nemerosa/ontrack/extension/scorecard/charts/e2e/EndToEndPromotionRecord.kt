package net.nemerosa.ontrack.extension.scorecard.charts.e2e

data class EndToEndPromotionRecord(
    val depth: Int,
    val ref: EndToEndPromotionNode,
    val target: EndToEndPromotionNode,
)