package net.nemerosa.ontrack.extension.casc.context

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.casc.context.core.admin.AdminContext
import net.nemerosa.ontrack.extension.casc.context.extensions.ExtensionsContext
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.json.schema.JsonObjectType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import org.springframework.stereotype.Component

@Component
class OntrackContext(
    private val configContext: ConfigContext,
    private val adminContext: AdminContext,
    private val extensionsContext: ExtensionsContext,
) : AbstractCascContext() {

    override fun run(node: JsonNode, paths: List<String>) {
        node.run(
            paths,
            "config" to configContext,
            "admin" to adminContext,
            "extensions" to extensionsContext,
        )
    }

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType {
        return JsonObjectType(
            title = "Ontrack Casc",
            description = "Ontrack Casc root object",
            properties = mapOf(
                "config" to configContext.jsonType(jsonTypeBuilder),
                "admin" to adminContext.jsonType(jsonTypeBuilder),
                "extensions" to extensionsContext.jsonType(jsonTypeBuilder),
            ),
            required = emptyList(),
            additionalProperties = false,
        )
    }

    override fun render(): JsonNode = mapOf(
        "config" to configContext.render(),
        "admin" to adminContext.render(),
        "extensions" to extensionsContext.render(),
    ).asJson()
}