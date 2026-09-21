package net.nemerosa.ontrack.extension.config.ci.conditions

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.config.ci.engine.CIEngine
import net.nemerosa.ontrack.model.docs.DocumentationExampleCode
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
@APIDescription("Checks the SCM branch name matches a regular expression.")
@DocumentationExampleCode(
    """
        name: branch
        config: '^release/.*$'
    """
)
class BranchCondition : Condition {
    override val name: String = "branch"
    override val schema: KClass<*> = String::class
    override val schemaDescription: String? = "Regular expression to match against the SCM branch name"
    override fun matches(
        conditionRegistry: ConditionRegistry,
        ciEngine: CIEngine,
        config: JsonNode,
        env: Map<String, String>
    ): Boolean {
        val ciBranchName = ciEngine.getBranchName(env)
        val regexBranchName = config.asText().toRegex()
        return regexBranchName.matches(ciBranchName ?: "")
    }
}