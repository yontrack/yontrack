package net.nemerosa.ontrack.extension.findings.report

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.json.schema.AbstractJsonSchemaProvider
import net.nemerosa.ontrack.model.json.schema.JsonSchemaBuilderService
import net.nemerosa.ontrack.model.support.EnvService
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

/**
 * JSON schema of the neutral format of the findings reports, on the Resources page.
 */
@Component
class FindingsJsonSchemaProvider(
    envService: EnvService,
    private val jsonSchemaBuilderService: JsonSchemaBuilderService,
) : AbstractJsonSchemaProvider(envService) {

    override val key: String = "findings"
    override val title: String = "Security findings report"
    override val description: String =
        "JSON schema for the reports of security scans in the neutral format of Yontrack (format `findings` of validateBuildWithFindings)"

    override fun createJsonSchema(): JsonNode =
        jsonSchemaBuilderService.createSchema(
            ref = key,
            id = id,
            title = title,
            description = description,
            root = FindingsReport::class,
        ).asJson().apply {
            allowNullForOptionalProperties(this)
        }

    /**
     * An optional field may be given as `null`, as the parser accepts it: the schema would
     * otherwise reject `"fixedVersion": null`.
     */
    private fun allowNullForOptionalProperties(node: JsonNode) {
        if (node is ObjectNode) {
            val properties = node.get("properties")
            if (properties is ObjectNode) {
                val required = node.get("required")?.values()?.map { it.asString() }?.toSet() ?: emptySet()
                properties.properties().forEach { (name, property) ->
                    if (name !in required && property is ObjectNode) {
                        val type = property.get("type")
                        if (type != null && type.isString) {
                            property.putArray("type").add(type.asString()).add("null")
                        }
                        val enum = property.get("enum")
                        if (enum is ArrayNode) {
                            enum.addNull()
                        }
                    }
                }
            }
            node.values().forEach { allowNullForOptionalProperties(it) }
        } else if (node is ArrayNode) {
            node.values().forEach { allowNullForOptionalProperties(it) }
        }
    }
}
