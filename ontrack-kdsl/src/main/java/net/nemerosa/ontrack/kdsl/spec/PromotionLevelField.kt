package net.nemerosa.ontrack.kdsl.spec

data class PromotionLevelField(
    val name: String,
    val displayName: String,
    val description: String?,
    val type: PromotionLevelFieldType,
    val required: Boolean,
    val options: List<String>,
    val position: Int,
)
