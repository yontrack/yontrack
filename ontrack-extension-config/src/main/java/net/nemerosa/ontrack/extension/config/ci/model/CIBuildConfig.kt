package net.nemerosa.ontrack.extension.config.ci.model

import com.fasterxml.jackson.annotation.JsonAnySetter
import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.config.schema.CIConfigExtensionJsonSchemaPropertiesContributorProvider
import net.nemerosa.ontrack.extension.config.schema.PropertiesJsonSchemaTypeProvider
import net.nemerosa.ontrack.model.json.schema.JsonSchemaPropertiesContributor
import net.nemerosa.ontrack.model.json.schema.JsonSchemaType

@APIDescription("Build configuration")
data class CIBuildConfig(
    @APIDescription("Name of the build as a template.")
    val buildName: String? = null,
    @APIDescription("List of properties for the build.")
    @JsonSchemaType(
        provider = PropertiesJsonSchemaTypeProvider::class,
        configuration = "BUILD",
    )
    override val properties: Map<String, JsonNode> = emptyMap(),
    @JsonSchemaPropertiesContributor(
        provider = CIConfigExtensionJsonSchemaPropertiesContributorProvider::class,
        configuration = "BUILD",
    )
    @field:JsonAnySetter
    override val extensions: Map<String, JsonNode> = mutableMapOf()
) : CIPropertiesConfig, CIExtensionsConfig
