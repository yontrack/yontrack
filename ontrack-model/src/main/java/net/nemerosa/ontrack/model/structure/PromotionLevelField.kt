package net.nemerosa.ontrack.model.structure

data class PromotionLevelField(
    val id: Int,
    val name: String,
    val displayName: String,
    val description: String?,
    val type: PromotionLevelFieldType,
    val required: Boolean,
    /** Non-empty only when type is CHOICE */
    val options: List<String>,
    val position: Int,
)
