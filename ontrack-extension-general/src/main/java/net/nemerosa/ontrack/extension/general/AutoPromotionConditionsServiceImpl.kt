package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Service

@Service
class AutoPromotionConditionsServiceImpl(
    private val propertyService: PropertyService,
    private val structureService: StructureService,
    private val validationRunService: ValidationRunService,
) : AutoPromotionConditionsService {

    override fun getConditions(promotionLevel: PromotionLevel): AutoPromotionConditions? =
        effectivePropertyOf(promotionLevel)?.let { (property, effective) ->
            AutoPromotionConditions(
                include = property.include,
                exclude = property.exclude,
                autoRevoke = property.autoRevoke,
                validationStamps = effective.validationStamps,
                promotionLevels = effective.promotionLevels,
            )
        }

    override fun getBuildConditions(promotionRun: PromotionRun): AutoPromotionBuildConditions? {
        val build = promotionRun.build
        return effectivePropertyOf(promotionRun.promotionLevel)?.let { (property, effective) ->
            AutoPromotionBuildConditions(
                include = property.include,
                exclude = property.exclude,
                autoRevoke = property.autoRevoke,
                validationStamps = effective.validationStamps.map { vs ->
                    AutoPromotionValidationStampCondition(
                        validationStamp = vs,
                        lastRun = structureService.getValidationRunsForBuildAndValidationStamp(
                            build = build,
                            validationStamp = vs,
                            offset = 0,
                            count = 1,
                        ).firstOrNull(),
                        // Same rule as the auto promotion itself
                        passed = validationRunService.isValidationRunPassed(build, vs),
                    )
                },
                promotionLevels = effective.promotionLevels.map { pl ->
                    AutoPromotionPromotionLevelCondition(
                        promotionLevel = pl,
                        promotionRun = structureService.getLastPromotionRunForBuildAndPromotionLevel(build, pl)
                            .orElse(null),
                    )
                },
            )
        }
    }

    /**
     * Auto promotion property of the promotion level, with what it requires on its branch - `null`
     * when the promotion level has no auto promotion.
     */
    private fun effectivePropertyOf(promotionLevel: PromotionLevel): EffectiveProperty? {
        val property = propertyService.getPropertyValue(promotionLevel, AutoPromotionPropertyType::class.java)
        if (property == null || property.isEmpty()) return null
        val branchId = promotionLevel.branch.id
        return EffectiveProperty(
            property = property,
            effective = AutoPromotionEffectivePrerequisites.of(
                property = property,
                branchValidationStamps = structureService.getValidationStampListForBranch(branchId),
                branchPromotionLevels = structureService.getPromotionLevelListForBranch(branchId),
            ),
        )
    }

    private data class EffectiveProperty(
        val property: AutoPromotionProperty,
        val effective: AutoPromotionEffectivePrerequisites,
    )
}
