package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.ValidationStamp

/**
 * What an [AutoPromotionProperty] actually requires on a branch: its explicitly named validation
 * stamps plus the branch's stamps matching `include` and not `exclude`, and its required promotion
 * levels - both in branch order.
 *
 * This is the list [AutoPromotionPrerequisites] evaluates, and the one the auto promotion conditions
 * display, so that what is shown never disagrees with what is evaluated.
 */
data class AutoPromotionEffectivePrerequisites(
    val validationStamps: List<ValidationStamp>,
    val promotionLevels: List<PromotionLevel>,
) {
    companion object {
        fun of(
            property: AutoPromotionProperty,
            branchValidationStamps: List<ValidationStamp>,
            branchPromotionLevels: List<PromotionLevel>,
        ) = AutoPromotionEffectivePrerequisites(
            validationStamps = branchValidationStamps.filter { vs -> property.contains(vs) },
            promotionLevels = branchPromotionLevels.filter { pl -> property.contains(pl) },
        )
    }
}
