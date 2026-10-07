package net.nemerosa.ontrack.model.readiness

import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel

/**
 * Computes the [readiness][Readiness] of a build: what it still lacks to reach a promotion level or a
 * slot.
 *
 * It reuses what already decides on promotions and deployments - the auto promotion conditions, the
 * promotion checks, the admission rules of the slots - and never re-implements them. It is read-only.
 *
 * Every method needs the `ProjectView` function on the build's project.
 */
interface ReadinessService {

    /**
     * Readiness of a build for a promotion level or for a slot - exactly one of them.
     *
     * @param build Build to check
     * @param promotionLevel Name of a promotion level of the build's branch
     * @param slotId ID of a slot of the build's project
     * @throws ReadinessInputException When not exactly one of [promotionLevel] and [slotId] is given,
     * or when they do not designate a target of the build
     */
    fun getReadiness(build: Build, promotionLevel: String?, slotId: String?): Readiness

    /**
     * Readiness of a build for a promotion level of its branch.
     *
     * @throws ReadinessInputException When the promotion level is not on the build's branch
     */
    fun getPromotionLevelReadiness(build: Build, promotionLevel: PromotionLevel): Readiness
}

/**
 * Wrong arguments for the readiness of a build.
 */
class ReadinessInputException(message: String) : InputException(message)
