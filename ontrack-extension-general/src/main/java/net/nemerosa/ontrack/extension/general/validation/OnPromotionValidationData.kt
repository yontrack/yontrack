package net.nemerosa.ontrack.extension.general.validation

/**
 * Data of a validation run granted because the linked build reached a promotion.
 *
 * @property buildId ID of the linked build
 * @property project Name of the project of the linked build
 * @property branch Name of the branch of the linked build
 * @property build Name of the linked build
 * @property promotion Name of the promotion level reached by the linked build
 * @property promotionRunId ID of the promotion run which granted the validation
 */
data class OnPromotionValidationData(
    val buildId: Int,
    val project: String,
    val branch: String,
    val build: String,
    val promotion: String,
    val promotionRunId: Int,
)
