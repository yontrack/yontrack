package net.nemerosa.ontrack.extension.general

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.json.schema.JsonStringType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.structure.PropertyAlias
import org.springframework.stereotype.Component

@Component
class ReleasePropertyAlias : PropertyAlias {
    override val alias: String = "release"
    override val type: String = ReleasePropertyType::class.java.name

    override fun parseConfig(data: JsonNode): JsonNode =
        ReleaseProperty(
            name = data.asText()
        ).asJson()

    override fun createJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType = JsonStringType("Release name")
}