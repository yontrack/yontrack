package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
class AutoPromotionPropertyTypeConfigurator(
    private val propertyService: PropertyService,
    private val structureService: StructureService,
) : PromotionLevelConfigurator {
    override fun configure(
        pl: PromotionLevel,
        config: PromotionLevelConfiguration
    ) {
        // `autoRevoke` alone has no prerequisite to check, so it is silently ignored here - consistent with
        // `AutoPromotionProperty.isEmpty()`
        if (config.validations.isEmpty() && config.promotions.isEmpty()) {
            propertyService.deleteProperty(pl, AutoPromotionPropertyType::class.java)
        } else {
            propertyService.editProperty(
                pl,
                AutoPromotionPropertyType::class.java,
                AutoPromotionProperty(
                    validationStamps = config.validations.map { name ->
                        structureService.setupValidationStamp(pl.branch, name, "")
                    },
                    promotionLevels = config.promotions.mapNotNull { name ->
                        structureService.findPromotionLevelByName(pl.project.name, pl.branch.name, name)
                            .getOrNull()
                    },
                    include = "",
                    exclude = "",
                    // A stored property is a resolved value: there is no "unspecified" state for it
                    autoRevoke = config.autoRevoke ?: false,
                )
            )
        }
    }
}