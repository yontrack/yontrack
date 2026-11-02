package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.json.schema.JsonBooleanType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.structure.PropertyAlias
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

@Component
class NotGrantedMedalsPropertyAlias : PropertyAlias {
    override val alias: String = "notGrantedMedals"
    override val type: String = NotGrantedMedalsPropertyType::class.java.name
    override fun parseConfig(data: JsonNode): JsonNode =
        NotGrantedMedalsProperty(
            enabled = data.asBoolean()
        ).asJson()

    override fun createJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        JsonBooleanType("Shows the medals of the auto-promoted levels a build does not have yet")
}
