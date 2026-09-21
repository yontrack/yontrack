package net.nemerosa.ontrack.model.structure

import tools.jackson.databind.JsonNode

/**
 * Request data for the creation of a property.
 */
data class PropertyCreationRequest(
        val propertyTypeName: String,
        val propertyData: JsonNode
)
