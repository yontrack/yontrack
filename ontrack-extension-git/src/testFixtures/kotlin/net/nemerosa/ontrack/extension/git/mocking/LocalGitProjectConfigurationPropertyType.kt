package net.nemerosa.ontrack.extension.git.mocking

import net.nemerosa.ontrack.extension.api.support.TestExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

/**
 * Test property associating a project with a local Git repository.
 */
@Component
class LocalGitProjectConfigurationPropertyType(
    testExtensionFeature: TestExtensionFeature
) : AbstractPropertyType<LocalGitProjectConfigurationProperty>(testExtensionFeature) {

    override val name: String = "Local Git configuration"

    override val description: String = "Local Git repository used for testing"

    override val supportedEntityTypes: Set<ProjectEntityType> = setOf(ProjectEntityType.PROJECT)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(LocalGitProjectConfigurationProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): LocalGitProjectConfigurationProperty = node.parse()

    override fun fromStorage(node: JsonNode): LocalGitProjectConfigurationProperty = node.parse()

    override fun replaceValue(
        value: LocalGitProjectConfigurationProperty,
        replacementFunction: (String) -> String
    ): LocalGitProjectConfigurationProperty = value
}
