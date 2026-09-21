package net.nemerosa.ontrack.extension.bitbucket.cloud.property

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.bitbucket.cloud.BitbucketCloudExtensionFeature
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.model.BitbucketCloudProject
import net.nemerosa.ontrack.extension.git.property.AbstractGitProjectConfigurationPropertyType
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.support.ConfigurationPropertyType
import org.springframework.stereotype.Component
import java.util.*

@Component
class BitbucketCloudProjectConfigurationPropertyType(
    extensionFeature: BitbucketCloudExtensionFeature,
    private val configurationService: BitbucketCloudConfigurationService,
    private val bitbucketCloudClientFactory: BitbucketCloudClientFactory,
) : AbstractGitProjectConfigurationPropertyType<BitbucketCloudProjectConfigurationProperty>(extensionFeature),
    ConfigurationPropertyType<BitbucketCloudConfiguration, BitbucketCloudProjectConfigurationProperty> {

    override val name: String = "Bitbucket Cloud configuration"

    override val description: String = "Associates the project with a Bitbucket Cloud repository"

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.of(ProjectEntityType.PROJECT)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(BitbucketCloudProjectConfigurationProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity.projectId(), ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): BitbucketCloudProjectConfigurationProperty = fromStorage(node)

    override fun fromStorage(node: JsonNode): BitbucketCloudProjectConfigurationProperty {
        val configurationName = node.path("configuration").asText()
        // Looks the configuration up
        val configuration: BitbucketCloudConfiguration = configurationService.getConfiguration(configurationName)
        // OK
        return BitbucketCloudProjectConfigurationProperty(
            configuration = configuration,
            workspace = node.path("workspace").asText(),
            repository = node.path("repository").asText(),
            indexationInterval = node.path("indexationInterval").asInt(),
            issueServiceConfigurationIdentifier = node.getTextField("issueServiceConfigurationIdentifier"),
        )
    }

    override fun forStorage(value: BitbucketCloudProjectConfigurationProperty): JsonNode {
        return mapOf(
            "configuration" to value.configuration.name,
            "workspace" to value.workspace,
            "repository" to value.repository,
            "indexationInterval" to value.indexationInterval,
            "issueServiceConfigurationIdentifier" to value.issueServiceConfigurationIdentifier,
        ).asJson()
    }

    override fun replaceValue(
        value: BitbucketCloudProjectConfigurationProperty,
        replacementFunction: (String) -> String
    ): BitbucketCloudProjectConfigurationProperty = BitbucketCloudProjectConfigurationProperty(
        value.configuration,
        value.workspace,
        replacementFunction(value.repository),
        value.indexationInterval,
        value.issueServiceConfigurationIdentifier
    )

    override fun getPropertyDecorations(value: BitbucketCloudProjectConfigurationProperty): Map<String, *> =
        getBitbucketCloudProject(value)?.run {
            mapOf(
                "projectInfo" to this
            )
        } ?: emptyMap<String, Any>()

    private fun getBitbucketCloudProject(property: BitbucketCloudProjectConfigurationProperty): BitbucketCloudProjectProperty? {
        return try {
            val client = bitbucketCloudClientFactory.getBitbucketCloudClient(property.configuration)
            val repository = client.getRepository(property.workspace, property.repository)
            BitbucketCloudProjectProperty(
                project = repository.project,
                url = "https://bitbucket.org/${property.workspace}/workspace/projects/${repository.project.key}"
            )
        } catch (_: Exception) {
            null
        }
    }

    class BitbucketCloudProjectProperty(
        val project: BitbucketCloudProject,
        val url: String,
    )
}
