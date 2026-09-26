package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun

/**
 * Exposes the conditions of an auto promotion, as [AutoPromotionPrerequisites] evaluates them.
 *
 * These are the *current* conditions and states: the auto promotion property is not versioned, so
 * this is not a record of what triggered a given promotion.
 */
interface AutoPromotionConditionsService {

    /**
     * Conditions of the auto promotion of a promotion level, or `null` when it has none.
     */
    fun getConditions(promotionLevel: PromotionLevel): AutoPromotionConditions?

    /**
     * Conditions of the auto promotion of the run's promotion level, with their state for the run's
     * build, or `null` when the promotion level has no auto promotion.
     */
    fun getBuildConditions(promotionRun: PromotionRun): AutoPromotionBuildConditions?
}
