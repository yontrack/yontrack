package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.extension.general.AutoPromotionPropertyType
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

/**
 * Which security stamps gate a promotion: the ones a promotion level of their branch requires
 * through its auto-promotion, by name or by pattern.
 */
@Component
class SecurityGating(
    private val structureService: StructureService,
    private val propertyService: PropertyService,
) {

    /**
     * Security stamps required by a promotion level of their branch, in the order given.
     */
    fun requiredStamps(stamps: List<SecurityStamp>): List<SecurityStamp> =
        stamps.groupBy { it.branchId }.flatMap { (branchId, branchStamps) ->
            val autoPromotions = structureService.getPromotionLevelListForBranch(ID.of(branchId))
                .mapNotNull { propertyService.getPropertyValue(it, AutoPromotionPropertyType::class.java) }
            if (autoPromotions.isEmpty()) {
                emptyList()
            } else {
                branchStamps.filter { stamp ->
                    val validationStamp = structureService.getValidationStamp(ID.of(stamp.id))
                    autoPromotions.any { validationStamp in it }
                }
            }
        }
}
