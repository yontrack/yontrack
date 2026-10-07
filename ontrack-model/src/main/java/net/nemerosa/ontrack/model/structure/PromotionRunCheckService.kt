package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.model.exceptions.InputException

/**
 * This service is called to check if a promotion run can actually be created or not.
 */
interface PromotionRunCheckService {

    /**
     * Checks if the given [promotionRun] can be created or not. Throws an [InputException] if the
     * promotion run cannot be created.
     */
    @Throws(InputException::class)
    fun checkPromotionRunCreation(promotionRun: PromotionRun)

    /**
     * Explains, without throwing, every reason why the [build] could not be promoted to the
     * [promotionLevel], all checks together.
     *
     * @return The reasons, in the order of the checks, empty when the promotion would be accepted
     */
    fun explainPromotionRunCreation(build: Build, promotionLevel: PromotionLevel): List<PromotionRunCheckReason>

}

/**
 * Reason why a promotion check would refuse a promotion.
 *
 * @property check Name of the check
 * @property reason Why the check would refuse the promotion
 */
data class PromotionRunCheckReason(
    val check: String,
    val reason: String,
)
