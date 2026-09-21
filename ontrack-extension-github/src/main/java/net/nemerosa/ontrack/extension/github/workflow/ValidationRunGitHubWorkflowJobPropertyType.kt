package net.nemerosa.ontrack.extension.github.workflow

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.github.GitHubExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.security.ValidationRunCreate
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

@Component
class ValidationRunGitHubWorkflowJobPropertyType(
    extensionFeature: GitHubExtensionFeature
) : AbstractPropertyType<ValidationRunGitHubWorkflowJobProperty>(
    extensionFeature
) {

    override val name: String = "GitHub Workflow Job"

    override val description: String = "Link to the GitHub Workflow Job which created this validation run."

    override val supportedEntityTypes = setOf(ProjectEntityType.VALIDATION_RUN)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(ValidationRunGitHubWorkflowJobProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, ValidationRunCreate::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): ValidationRunGitHubWorkflowJobProperty = node.parse()

    override fun fromStorage(node: JsonNode): ValidationRunGitHubWorkflowJobProperty = node.parse()

    override fun replaceValue(
        value: ValidationRunGitHubWorkflowJobProperty,
        replacementFunction: (String) -> String
    ): ValidationRunGitHubWorkflowJobProperty = value

}