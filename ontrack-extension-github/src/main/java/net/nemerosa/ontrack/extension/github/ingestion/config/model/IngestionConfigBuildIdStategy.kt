package net.nemerosa.ontrack.extension.github.ingestion.config.model

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.common.api.APIDescription

@APIDescription("Build identification strategy")
data class IngestionConfigBuildIdStategy(
    @APIDescription("ID of the build identification strategy (null to use the default commit-based strategy)")
    val id: String? = null,
    @APIDescription("Configuration of the build identification strategy")
    val config: JsonNode = NullNode.instance,
)