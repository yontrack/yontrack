package net.nemerosa.ontrack.extension.bitbucket.cloud.property

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.bitbucket.cloud.BitbucketCloudExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.BuildConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

/**
 * The FQCN of this class is the persistent storage ID of the property: never rename or move it.
 */
@Component
class BuildBitbucketPipelineRunPropertyType(
    extensionFeature: BitbucketCloudExtensionFeature,
) : AbstractPropertyType<BuildBitbucketPipelineRunProperty>(extensionFeature) {

    override val name: String = "Bitbucket Pipelines run"

    override val description: String = "Link to the Bitbucket Pipelines run which created this build."

    override val supportedEntityTypes = setOf(ProjectEntityType.BUILD)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(BuildBitbucketPipelineRunProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, BuildConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): BuildBitbucketPipelineRunProperty = node.parse()

    override fun fromStorage(node: JsonNode): BuildBitbucketPipelineRunProperty = node.parse()

    override fun replaceValue(
        value: BuildBitbucketPipelineRunProperty,
        replacementFunction: (String) -> String,
    ): BuildBitbucketPipelineRunProperty = value
}
