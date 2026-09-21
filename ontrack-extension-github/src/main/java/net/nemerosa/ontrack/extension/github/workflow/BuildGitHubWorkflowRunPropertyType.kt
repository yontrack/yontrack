package net.nemerosa.ontrack.extension.github.workflow

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.github.GitHubExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.BuildConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertySearchArguments
import org.springframework.stereotype.Component

@Component
class BuildGitHubWorkflowRunPropertyType(
    extensionFeature: GitHubExtensionFeature,
) : AbstractPropertyType<BuildGitHubWorkflowRunProperty>(
    extensionFeature
) {

    override val name: String = "GitHub Workflow Run"

    override val description: String = "Link to the GitHub Workflow Run which created this build."

    override val supportedEntityTypes = setOf(ProjectEntityType.BUILD)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(BuildGitHubWorkflowRunProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, BuildConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): BuildGitHubWorkflowRunProperty = node.parse()

    override fun fromStorage(node: JsonNode): BuildGitHubWorkflowRunProperty =
        if (node.has(BuildGitHubWorkflowRunProperty::workflows.name)) {
            node.parse()
        } else {
            // Legacy
            val workflow = node.parse<BuildGitHubWorkflowRun>()
            BuildGitHubWorkflowRunProperty(workflows = listOf(workflow))
        }

    override fun replaceValue(
        value: BuildGitHubWorkflowRunProperty,
        replacementFunction: (String) -> String,
    ): BuildGitHubWorkflowRunProperty = value

    override fun getSearchArguments(token: String): PropertySearchArguments? =
        if (token.isNotBlank()) {
            try {
                val value = token.toLong()
                PropertySearchArguments(
                    jsonContext = "jsonb_array_elements(pp.json->'workflows') as workflow",
                    jsonCriteria = "(workflow->>'runId')::bigint = :value",
                    criteriaParams = mapOf(
                        "value" to value
                    )
                )
            } catch (_: NumberFormatException) {
                null
            }
        } else {
            null
        }
}