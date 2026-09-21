package net.nemerosa.ontrack.kdsl.spec.extension.general

import net.nemerosa.ontrack.kdsl.spec.PromotionLevel
import net.nemerosa.ontrack.kdsl.spec.deleteProperty
import net.nemerosa.ontrack.kdsl.spec.getProperty
import net.nemerosa.ontrack.kdsl.spec.setProperty

/**
 * Sets a promotion dependencies property on a promotion level.
 */
var PromotionLevel.promotionDependencies: List<String>?
    get() = getProperty(PROMOTION_DEPENDENCIES_PROPERTY)?.path("dependencies")?.values()?.map { it.asText() }
    set(value) {
        if (value != null) {
            setProperty(PROMOTION_DEPENDENCIES_PROPERTY, mapOf("dependencies" to value))
        } else {
            deleteProperty(PROMOTION_DEPENDENCIES_PROPERTY)
        }
    }

const val PROMOTION_DEPENDENCIES_PROPERTY = "net.nemerosa.ontrack.extension.general.PromotionDependenciesPropertyType"