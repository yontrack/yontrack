package net.nemerosa.ontrack.extension.config.model

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription

@APIDescription("Property configuration")
data class PropertyConfiguration(
    @APIDescription("FQCN of the property type")
    val type: String,
    @APIDescription("Configuration data for the property. When null, the property is deleted.")
    val data: JsonNode,
)

/**
 * Merges the properties of a custom configuration into the default ones, by property type. The last one wins,
 * so that a custom `null` cancels a default value, a custom value overrides a default `null`, and each type is
 * applied only once.
 */
fun mergeProperties(
    defaults: List<PropertyConfiguration>,
    customs: List<PropertyConfiguration>,
): List<PropertyConfiguration> =
    (defaults + customs).associateBy { it.type }.values.toList()
