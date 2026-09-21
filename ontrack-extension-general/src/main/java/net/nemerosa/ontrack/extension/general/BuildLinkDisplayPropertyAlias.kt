package net.nemerosa.ontrack.extension.general

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.json.schema.JsonBooleanType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.structure.PropertyAlias
import org.springframework.stereotype.Component

@Component
class BuildLinkDisplayPropertyAlias : PropertyAlias {
    override val alias: String = "useLabel"
    override val type: String = BuildLinkDisplayPropertyType::class.java.name
    override fun parseConfig(data: JsonNode): JsonNode =
        BuildLinkDisplayProperty(
            useLabel = data.asBoolean()
        ).asJson()

    override fun createJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType = JsonBooleanType("Use label")
}