package net.nemerosa.ontrack.extension.config.ci.conditions

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.config.ci.engine.CIEngine
import net.nemerosa.ontrack.extension.config.ci.model.CIConditionConfig
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.docs.DocumentationExampleCode
import net.nemerosa.ontrack.model.json.schema.JsonSchemaListWrapper
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
@APIDescription("OR condition. Conditions are evaluated in order, and at least one must be true.")
@DocumentationExampleCode(
    """
        name: or
        config:
           - name: branch
             config: main
           - name: environment-regex:
             config:
                name: VERSION
                regex: '\d+\.\d+\.\d+'
    """
)
class OrCondition : Condition {

    override val name: String = "or"

    override val schema: KClass<*> = OrConditionConfig::class
    override val schemaDescription: String = "List of conditions"

    override fun matches(
        conditionRegistry: ConditionRegistry,
        ciEngine: CIEngine,
        config: JsonNode,
        env: Map<String, String>
    ): Boolean {
        val conditionConfigs = config.values().map { it.parse<CIConditionConfig>() }
        if (conditionConfigs.isEmpty()) {
            return false
        } else {
            return conditionConfigs.any { (name, conditionConfig) ->
                val condition = conditionRegistry.getCondition(name)
                condition.matches(conditionRegistry, ciEngine, conditionConfig, env)
            }
        }
    }

    @JsonSchemaListWrapper(listProperty = "conditions")
    class OrConditionConfig(
        val conditions: List<CIConditionConfig>
    )

}