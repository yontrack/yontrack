package net.nemerosa.ontrack.extension.av.config

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.json.schema.AbstractJsonSchemaProvider
import net.nemerosa.ontrack.model.json.schema.JsonSchemaBuilderService
import net.nemerosa.ontrack.model.support.EnvService
import org.springframework.stereotype.Component

@Component
class AutoVersioningJsonSchemaProvider(
    envService: EnvService,
    private val jsonSchemaBuilderService: JsonSchemaBuilderService,
) : AbstractJsonSchemaProvider(envService) {

    override val key: String = "auto-versioning"
    override val title: String = "Auto-versioning configuration"
    override val description: String = "JSON schema for the auto-versioning configuration of a branch"

    override fun createJsonSchema(): JsonNode =
        jsonSchemaBuilderService.createSchema(
            ref = "auto-versioning",
            id = id,
            title = title,
            description = description,
            root = AutoVersioningConfig::class,
        ).asJson()
}
