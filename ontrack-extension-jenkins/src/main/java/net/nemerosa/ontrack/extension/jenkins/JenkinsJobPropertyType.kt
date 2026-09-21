package net.nemerosa.ontrack.extension.jenkins

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component
import java.util.*

@Component
class JenkinsJobPropertyType(
    extensionFeature: JenkinsExtensionFeature,
    configurationService: JenkinsConfigurationService
) : AbstractJenkinsPropertyType<JenkinsJobProperty>(extensionFeature, configurationService) {
    override val name: String = "Jenkins Job"

    override val description: String = "Link to a Jenkins Job"

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.of(
        ProjectEntityType.PROJECT,
        ProjectEntityType.BRANCH,
        ProjectEntityType.PROMOTION_LEVEL,
        ProjectEntityType.VALIDATION_STAMP
    )

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(JenkinsJobProperty::class)

    /**
     * Only granted for project configurators.
     */
    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return securityService.isProjectFunctionGranted(entity.projectId(), ProjectConfig::class.java)
    }

    /**
     * Everybody can see the property value.
     */
    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return true
    }

    override fun forStorage(value: JenkinsJobProperty): JsonNode {
        return mapOf(
            "configuration" to value.configuration.name,
            "job" to value.job,
        ).asJson()
    }

    override fun fromClient(node: JsonNode): JenkinsJobProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): JenkinsJobProperty {
        val configurationName = node.path("configuration").asText()
        val job = node.path("job").asText()
        // Looks the configuration up
        val configuration = loadConfiguration(configurationName)
        // Validates the job name
        validateNotBlank(job, "The Jenkins Job name must not be empty")
        // OK
        return JenkinsJobProperty(
            configuration,
            job
        )
    }

    override fun replaceValue(
        value: JenkinsJobProperty,
        replacementFunction: (String) -> String
    ): JenkinsJobProperty {
        return JenkinsJobProperty(
            replaceConfiguration(value.configuration, replacementFunction),
            replacementFunction(value.job)
        )
    }
}
