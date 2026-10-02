package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.structure.Build
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

    /**
     * Conditions of the auto promotion of a promotion level, with their state for a build, whether
     * the build is promoted to this level or not - `null` when the promotion level has no auto promotion.
     *
     * @throws AutoPromotionConditionsBranchMismatchException When the promotion level is not on the
     * build's branch
     */
    fun getBuildConditions(build: Build, promotionLevel: PromotionLevel): AutoPromotionBuildConditions?
}
