package net.nemerosa.ontrack.extension.casc.context

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.json.schema.JsonObjectType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder

abstract class AbstractHolderContext<T : SubCascContext>(
    private val subContexts: List<T>,
    private val description: String,
) : AbstractCascContext() {

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType {
        return JsonObjectType(
            title = this::class.java.simpleName,
            description = description,
            properties = subContexts.associate { ctx ->
                ctx.field to ctx.jsonType(jsonTypeBuilder)
            },
            required = emptyList(),
        )
    }

    override fun run(node: JsonNode, paths: List<String>) {
        node.runSubCascContexts(paths, subContexts)
    }

    override fun render(): JsonNode = subContexts.associate {
        it.field to it.render()
    }.asJson()
}
