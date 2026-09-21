package net.nemerosa.ontrack.extension.av.properties

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.node.StringNode
import org.springframework.stereotype.Component

@Component
class NpmFilePropertyType : AbstractJsonFilePropertyType() {

    override val id: String = "npm"

    /**
     * Most of the time when you put dependencies in peerDependencies section you put it also in devDependencies
     * This means it need to be updated in both places which is not supported now. The best approach here would be
     * to inject targetVersion into postProcessingConfig so you can do replacement there for such custom configs.
     */
    fun getDependencies(content: JsonNode, targetProperty: String?): JsonNode? {
        val dependencies = content["dependencies"]
        val optionalDependencies = content["optionalDependencies"]
        val peerDependencies = content["peerDependencies"]
        val devDependencies = content["devDependencies"]

        return if (dependencies?.get(targetProperty) != null) {
            dependencies
        } else if (optionalDependencies?.get(targetProperty) != null) {
            optionalDependencies
        } else if (peerDependencies?.get(targetProperty) != null) {
            peerDependencies
        } else if (devDependencies?.get(targetProperty) != null) {
            devDependencies
        } else {
            null
        }
    }

    override fun readProperty(content: JsonNode, targetProperty: String?): String? {
        val property = targetProperty ?: error("targetProperty must be defined")
        val dependencies: JsonNode? = getDependencies(content, targetProperty)
        val value = dependencies?.get(property)?.stringValueOpt()?.orElse(null)
        return value?.run {
            if (startsWith("^")) {
                substring(1)
            } else {
                this
            }
        }
    }

    override fun replaceProperty(content: JsonNode, targetProperty: String?, targetVersion: String): JsonNode {
        val property = targetProperty ?: error("targetProperty must be defined")
        val dependencies: JsonNode? = getDependencies(content, targetProperty)
        if (dependencies != null && dependencies is ObjectNode) {
            dependencies.put(property, "^$targetVersion")
        }
        return content
    }
}