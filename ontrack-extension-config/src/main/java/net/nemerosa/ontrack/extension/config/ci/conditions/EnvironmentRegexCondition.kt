package net.nemerosa.ontrack.extension.config.ci.conditions

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.config.ci.engine.CIEngine
import net.nemerosa.ontrack.json.parseOrNull
import net.nemerosa.ontrack.model.docs.DocumentationExampleCode
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
@APIDescription("Condition based on checking the value of an environment variable against a regular expression")
@DocumentationExampleCode(
    """
        name: environment-regex
        config:
            name: VERSION
            regex: '\d+\.\d+\.\d+'
    """
)
class EnvironmentRegexCondition : Condition {

    override val name: String = "environment-regex"

    override val schema: KClass<*> = EnvironmentRegexConditionConfig::class

    override fun matches(
        conditionRegistry: ConditionRegistry,
        ciEngine: CIEngine,
        config: JsonNode,
        env: Map<String, String>
    ): Boolean {
        val envConfig = config.parseOrNull<EnvironmentRegexConditionConfig>()
            ?: throw ConditionConfigException(name)
        // If no value, condition evaluates to false
        val value = env[envConfig.name] ?: return false
        // Checking the regular expression
        return envConfig.regex.toRegex().matches(value)
    }
}

class EnvironmentRegexConditionConfig(
    val name: String,
    val regex: String,
)