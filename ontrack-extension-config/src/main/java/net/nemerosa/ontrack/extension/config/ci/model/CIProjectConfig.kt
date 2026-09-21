package net.nemerosa.ontrack.extension.config.ci.model

import com.fasterxml.jackson.annotation.JsonAnySetter
import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.config.model.ProjectIssueServiceIdentifier
import net.nemerosa.ontrack.extension.config.schema.CIConfigExtensionJsonSchemaPropertiesContributorProvider
import net.nemerosa.ontrack.extension.config.schema.PropertiesJsonSchemaTypeProvider
import net.nemerosa.ontrack.model.json.schema.JsonSchemaPropertiesContributor
import net.nemerosa.ontrack.model.json.schema.JsonSchemaType

@APIDescription("Project configuration")
data class CIProjectConfig(
    @APIDescription("List of properties for the build.")
    @JsonSchemaType(
        provider = PropertiesJsonSchemaTypeProvider::class,
        configuration = "PROJECT",
    )
    override val properties: Map<String, JsonNode> = emptyMap(),
    @APIDescription("Overriding the name of the project")
    val name: String? = null,
    @APIDescription("Name of the SCM configuration to use for this project")
    val scmConfig: String? = null,
    @APIDescription("Issue service configuration for this project")
    val issueServiceIdentifier: ProjectIssueServiceIdentifier? = null,
    @APIDescription("SCM indexation interval (in minutes)")
    val scmIndexationInterval: Int? = null,
    @JsonSchemaPropertiesContributor(
        provider = CIConfigExtensionJsonSchemaPropertiesContributorProvider::class,
        configuration = "PROJECT",
    )
    @field:JsonAnySetter
    override val extensions: Map<String, JsonNode> = mutableMapOf()
) : CIPropertiesConfig, CIExtensionsConfig
