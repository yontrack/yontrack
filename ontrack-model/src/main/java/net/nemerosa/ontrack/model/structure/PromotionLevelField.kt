package net.nemerosa.ontrack.model.structure

data class PromotionLevelField(
    val id: Int = 0,
    val name: String,
    val displayName: String,
    val description: String?,
    val type: PromotionLevelFieldType,
    val required: Boolean = false,
    /** Non-empty only when type is CHOICE */
    val options: List<String> = emptyList(),
    val position: Int = 0,
)
