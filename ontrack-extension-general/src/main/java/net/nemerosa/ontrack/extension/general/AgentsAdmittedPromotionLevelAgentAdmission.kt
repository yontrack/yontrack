package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.security.PromotionLevelAgentAdmission
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component

/**
 * A promotion level admits agents when it has the [AgentsAdmittedPropertyType] property, with its
 * flag set.
 */
@Component
class AgentsAdmittedPromotionLevelAgentAdmission(
    private val propertyService: PropertyService,
) : PromotionLevelAgentAdmission {

    override fun isAgentsAdmitted(promotionLevel: PromotionLevel): Boolean =
        propertyService.getPropertyValue(promotionLevel, AgentsAdmittedPropertyType::class.java)
            ?.admitted
            ?: false
}
