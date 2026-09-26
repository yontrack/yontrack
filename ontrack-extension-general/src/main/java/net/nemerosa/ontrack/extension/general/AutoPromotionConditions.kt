package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationStamp

@APIDescription(
    "Current conditions of the auto promotion of a promotion level: the validation stamps and promotion " +
            "levels it requires, as they are resolved on the branch today."
)
data class AutoPromotionConditions(
    @APIDescription("Regular expression including validation stamps by name - empty when not set")
    val include: String,
    @APIDescription("Regular expression excluding validation stamps by name - empty when not set")
    val exclude: String,
    @APIDescription("Is the promotion revoked when one of its prerequisites is no longer valid?")
    val autoRevoke: Boolean,
    @APIDescription(
        "Validation stamps required for the promotion, in branch order: the ones named explicitly, plus " +
                "the ones of the branch matching the include expression and not the exclude one."
    )
    val validationStamps: List<ValidationStamp>,
    @APIDescription("Promotion levels required for the promotion, in branch order")
    val promotionLevels: List<PromotionLevel>,
)

@APIDescription(
    "Current conditions of the auto promotion of a promotion level, and their current state for a given build."
)
data class AutoPromotionBuildConditions(
    @APIDescription("Regular expression including validation stamps by name - empty when not set")
    val include: String,
    @APIDescription("Regular expression excluding validation stamps by name - empty when not set")
    val exclude: String,
    @APIDescription("Is the promotion revoked when one of its prerequisites is no longer valid?")
    val autoRevoke: Boolean,
    @APIDescription("State of each required validation stamp for the build, in branch order")
    val validationStamps: List<AutoPromotionValidationStampCondition>,
    @APIDescription("State of each required promotion level for the build, in branch order")
    val promotionLevels: List<AutoPromotionPromotionLevelCondition>,
)

@APIDescription("State of a validation stamp required by an auto promotion, for a build")
data class AutoPromotionValidationStampCondition(
    @APIDescription("Required validation stamp")
    val validationStamp: ValidationStamp,
    @APIDescription("Latest run of the validation stamp for the build - null when it never ran")
    val lastRun: ValidationRun?,
    @APIDescription("Does the latest run count as passed for the auto promotion?")
    val passed: Boolean,
)

@APIDescription("State of a promotion level required by an auto promotion, for a build")
data class AutoPromotionPromotionLevelCondition(
    @APIDescription("Required promotion level")
    val promotionLevel: PromotionLevel,
    @APIDescription("Latest run of the promotion level for the build - null when it is not granted")
    val promotionRun: PromotionRun?,
)
