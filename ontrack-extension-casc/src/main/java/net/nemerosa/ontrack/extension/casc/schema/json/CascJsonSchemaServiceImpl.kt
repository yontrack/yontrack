package net.nemerosa.ontrack.extension.casc.schema.json

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.casc.context.OntrackContext
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.json.schema.*
import net.nemerosa.ontrack.model.support.EnvService
import org.springframework.stereotype.Service

@Service
class CascJsonSchemaServiceImpl(
    envService: EnvService,
    private val jsonSchemaBuilderService: JsonSchemaBuilderService,
    private val ontrackContext: OntrackContext,
) : AbstractJsonSchemaProvider(envService), CascJsonSchemaService {

    override val key: String = "casc"
    override val title: String = "Yontrack CasC"
    override val description: String = "Configuration as code for Yontrack"

    override fun createJsonSchema(): JsonNode {
        return jsonSchemaBuilderService.createSchema(
            ref = "casc",
            id = id,
            title = title,
            description = description,
            root = object : JsonTypeProvider {
                override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType {
                    return JsonObjectType(
                        title = "Root Ontrack CasC",
                        description = "Ontrack configuration as code",
                        properties = mapOf(
                            "ontrack" to ontrackContext.jsonType(jsonTypeBuilder),
                        ),
                        required = listOf("ontrack"),
                        additionalProperties = false,
                    )
                }
            }
        ).asJson()
    }

}