package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel

/**
 * Asking for the auto promotion conditions of a build against a promotion level of another branch.
 */
class AutoPromotionConditionsBranchMismatchException(
    build: Build,
    promotionLevel: PromotionLevel,
) : InputException(
    "Promotion level ${promotionLevel.entityDisplayName} does not belong to the branch of ${build.entityDisplayName}."
)
