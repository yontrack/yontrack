package net.nemerosa.ontrack.kdsl.spec.extension.agents

import net.nemerosa.ontrack.kdsl.spec.PromotionLevel
import net.nemerosa.ontrack.kdsl.spec.deleteProperty
import net.nemerosa.ontrack.kdsl.spec.getProperty
import net.nemerosa.ontrack.kdsl.spec.setProperty

const val ASSISTED_BUILDS_REQUIRE_PROPERTY =
    "net.nemerosa.ontrack.extension.agents.assisted.AssistedBuildsRequirePropertyType"

/**
 * ID of the licensed feature of the agent governance, which the *Assisted builds require* property needs
 */
const val FEATURE_AGENTS = "extension.agents"

/**
 * *Assisted builds require* on a promotion level: the names of the validation stamps an assisted build
 * must pass before being promoted to the level. `null` when the property is not set.
 */
var PromotionLevel.assistedBuildsRequire: List<String>?
    get() = getProperty(ASSISTED_BUILDS_REQUIRE_PROPERTY)?.path("validationStamps")?.values()?.map { it.asText() }
    set(value) {
        if (value != null) {
            setProperty(ASSISTED_BUILDS_REQUIRE_PROPERTY, mapOf("validationStamps" to value))
        } else {
            deleteProperty(ASSISTED_BUILDS_REQUIRE_PROPERTY)
        }
    }
