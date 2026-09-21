package net.nemerosa.ontrack.extension.config.ci.model

import com.fasterxml.jackson.annotation.JsonAnySetter
import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.config.schema.CIConfigExtensionJsonSchemaPropertiesContributorProvider
import net.nemerosa.ontrack.extension.config.schema.PropertiesJsonSchemaTypeProvider
import net.nemerosa.ontrack.extension.config.schema.ValidationJsonSchemaMapValueTypeProvider
import net.nemerosa.ontrack.model.json.schema.JsonSchemaMapValueType
import net.nemerosa.ontrack.model.json.schema.JsonSchemaPropertiesContributor
import net.nemerosa.ontrack.model.json.schema.JsonSchemaType

@APIDescription("Branch configuration")
data class CIBranchConfig(
    @APIDescription("List of properties for the branch.")
    @JsonSchemaType(
        provider = PropertiesJsonSchemaTypeProvider::class,
        configuration = "BRANCH",
    )
    override val properties: Map<String, JsonNode> = emptyMap(),
    @APIDescription("List of validation stamps to define for the branch.")
    @JsonSchemaMapValueType(
        provider = ValidationJsonSchemaMapValueTypeProvider::class,
    )
    val validations: Map<String, JsonNode> = emptyMap(),
    @APIDescription("List of promotion levels to define for the branch.")
    val promotions: Map<String, CIPromotionConfig> = emptyMap(),
    @JsonSchemaPropertiesContributor(
        provider = CIConfigExtensionJsonSchemaPropertiesContributorProvider::class,
        configuration = "BRANCH",
    )
    @field:JsonAnySetter
    override val extensions: Map<String, JsonNode> = mutableMapOf()
) : CIPropertiesConfig, CIExtensionsConfig
