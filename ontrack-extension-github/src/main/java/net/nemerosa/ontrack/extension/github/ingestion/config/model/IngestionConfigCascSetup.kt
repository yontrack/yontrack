package net.nemerosa.ontrack.extension.github.ingestion.config.model

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.common.api.APIDescription

/**
 * CasC for a given entity.
 */
data class IngestionConfigCascSetup(
    @APIDescription("Regular expression for the branches which can setup the entity")
    val includes: String = "main",
    @APIDescription("Regular expression to exclude branches")
    val excludes: String = "",
    @APIDescription("Casc configuration for the entity")
    val casc: JsonNode = NullNode.instance,
)