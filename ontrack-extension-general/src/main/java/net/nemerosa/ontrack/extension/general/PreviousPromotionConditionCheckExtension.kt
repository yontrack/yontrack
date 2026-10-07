package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.extension.api.PromotionRunCheckExtension
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

/**
 * [PromotionRunCheckExtension] based on the [PreviousPromotionConditionPropertyType] property value.
 *
 * The cascade itself lives in [PreviousPromotionConditionService], which the delivery map also reads:
 * what this check refuses and what the map draws are then the same answer by construction.
 */
@Component
class PreviousPromotionConditionCheckExtension(
        private val structureService: StructureService,
        extensionFeature: GeneralExtensionFeature,
        private val previousPromotionConditionService: PreviousPromotionConditionService,
) : AbstractExtension(extensionFeature), PromotionRunCheckExtension {

    override fun checkPromotionRunCreation(promotionRun: PromotionRun) {
        refusal(promotionRun.build, promotionRun.promotionLevel)?.let { throw it }
    }

    /**
     * Only the level right before the promotion is ever required: there is one reason at most.
     */
    override fun explainPromotionRunCreation(build: Build, promotionLevel: PromotionLevel): List<String> =
        listOfNotNull(refusal(build, promotionLevel)?.message?.trim()?.replace(Regex("\\s+"), " "))

    /**
     * Why the [build] cannot be promoted to [promotion], or `null` when it can.
     */
    private fun refusal(build: Build, promotion: PromotionLevel): InputException? {
        // List of all promotions for the branch
        val promotions = structureService.getPromotionLevelListForBranch(promotion.branch.id)
        // Index of the promotion to grant
        val index = promotions.indexOfFirst { it.id() == promotion.id() }
        // There is a previous promotion
        if (index > 0) {
            val previousPromotion = promotions[index - 1]
            // Checks if the build is granted this promotion
            val previousPromotions = structureService.getPromotionRunsForBuildAndPromotionLevel(build, previousPromotion)
            val previousPromotionGranted = previousPromotions.isNotEmpty()
            // If previous promotion NOT granted, we have to check the configuration
            // If not, this does not matter
            if (!previousPromotionGranted) {
                val resolution = previousPromotionConditionService.resolvePreviousPromotionCondition(promotion)
                if (resolution.required) {
                    // Which exception says WHERE the refusal comes from, which is the question the
                    // user actually asks - the delivery map deliberately does not answer it
                    val source = resolution.source
                    return if (source != null) {
                        PreviousPromotionRequiredException(previousPromotion, promotion, source)
                    } else {
                        PreviousPromotionRequiredGlobalException(previousPromotion, promotion)
                    }
                }
            }
        }
        return null
    }

    override val checkName: String = "Previous promotion condition"

    override val order: Int = 0
}
