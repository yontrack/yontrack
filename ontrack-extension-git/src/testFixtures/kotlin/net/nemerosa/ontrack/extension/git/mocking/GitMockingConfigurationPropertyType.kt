package net.nemerosa.ontrack.extension.git.mocking

import tools.jackson.databind.JsonNode
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

@Component
class GitMockingConfigurationPropertyType(
        testExtensionFeature: TestExtensionFeature
) : AbstractPropertyType<GitMockingConfigurationProperty>(testExtensionFeature) {

    override val name: String = "Mock Git configuration"

    override val description: String = "Git configuration used for testing"

    override val supportedEntityTypes: Set<ProjectEntityType> = setOf(ProjectEntityType.PROJECT)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(GitMockingConfigurationProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
            securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): GitMockingConfigurationProperty = error("Will not be used")

    override fun fromStorage(node: JsonNode): GitMockingConfigurationProperty = node.parse()

    override fun replaceValue(value: GitMockingConfigurationProperty, replacementFunction: (String) -> String): GitMockingConfigurationProperty = error("Will not be used")
}