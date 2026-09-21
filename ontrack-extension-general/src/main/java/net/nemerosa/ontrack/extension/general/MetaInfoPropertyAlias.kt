package net.nemerosa.ontrack.extension.general

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonArrayType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.structure.PropertyAlias
import org.springframework.stereotype.Component

@Component
class MetaInfoPropertyAlias : PropertyAlias {
    override val alias: String = "metaInfo"

    override val type: String = MetaInfoPropertyType::class.java.name

    override fun parseConfig(data: JsonNode): JsonNode = data.values().map {
        it.parse<MetaInfoPropertyItem>()
    }.let {
        MetaInfoProperty(items = it)
    }.asJson()

    override fun createJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        JsonArrayType(
            items = jsonTypeBuilder.toType(MetaInfoPropertyItem::class),
            description = "List of meta information items"
        )

}