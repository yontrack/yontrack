package net.nemerosa.ontrack.extension.config.ci.conditions

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.config.ci.engine.CIEngine
import kotlin.reflect.KClass

interface Condition {
    val name: String

    fun matches(
        conditionRegistry: ConditionRegistry,
        ciEngine: CIEngine,
        config: JsonNode,
        env: Map<String, String>
    ): Boolean

    /**
     * Returns a class representing the schema for this condition.
     */
    val schema: KClass<*>

    /**
     * Description for the schema
     */
    val schemaDescription: String? get() = null
}
