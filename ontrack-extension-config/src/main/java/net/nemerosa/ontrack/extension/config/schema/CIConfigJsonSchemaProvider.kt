package net.nemerosa.ontrack.extension.config.schema

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.config.ci.model.CIConfigInput
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.json.schema.AbstractJsonSchemaProvider
import net.nemerosa.ontrack.model.json.schema.JsonSchemaBuilderService
import net.nemerosa.ontrack.model.support.EnvService
import org.springframework.stereotype.Component

@Component
class CIConfigJsonSchemaProvider(
    envService: EnvService,
    private val jsonSchemaBuilderService: JsonSchemaBuilderService,
) : AbstractJsonSchemaProvider(envService) {

    override val key: String = "ci-config"
    override val title: String = "CI configuration"
    override val description: String = "JSON schema for the CI configuration"

    override fun createJsonSchema(): JsonNode =
        jsonSchemaBuilderService.createSchema(
            ref = "ci-config",
            id = id,
            title = title,
            description = description,
            root = CIConfigInput::class,
        ).asJson()
}