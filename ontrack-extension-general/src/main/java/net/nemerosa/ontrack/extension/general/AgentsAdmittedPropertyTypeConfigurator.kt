package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionLevelConfiguration
import net.nemerosa.ontrack.model.structure.PromotionLevelConfigurator
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component

/**
 * `agents` on a promotion of the CI configuration: `true` sets the [AgentsAdmittedPropertyType]
 * property, `false` removes it, and leaving it out keeps whatever was set otherwise (UI, CasC).
 */
@Component
class AgentsAdmittedPropertyTypeConfigurator(
    private val propertyService: PropertyService,
) : PromotionLevelConfigurator {

    override fun configure(pl: PromotionLevel, config: PromotionLevelConfiguration) {
        when (config.agents) {
            true -> propertyService.editProperty(
                pl,
                AgentsAdmittedPropertyType::class.java,
                AgentsAdmittedProperty(admitted = true),
            )

            false -> propertyService.deleteProperty(pl, AgentsAdmittedPropertyType::class.java)
            null -> {}
        }
    }
}
