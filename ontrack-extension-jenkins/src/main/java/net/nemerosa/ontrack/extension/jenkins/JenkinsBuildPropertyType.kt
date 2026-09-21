package net.nemerosa.ontrack.extension.jenkins

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.exceptions.PropertyUnsupportedEntityTypeException
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.BuildCreate
import net.nemerosa.ontrack.model.security.PromotionRunCreate
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.security.ValidationRunCreate
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component
import java.util.*

@Component
class JenkinsBuildPropertyType(
    extensionFeature: JenkinsExtensionFeature,
    configurationService: JenkinsConfigurationService
) : AbstractJenkinsPropertyType<JenkinsBuildProperty>(extensionFeature, configurationService) {
    override val name: String = "Jenkins Build"

    override val description: String = "Link to a Jenkins Build"

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.of(
        ProjectEntityType.BUILD,
        ProjectEntityType.PROMOTION_RUN,
        ProjectEntityType.VALIDATION_RUN
    )

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(JenkinsBuildProperty::class)

    /**
     * Depends on the nature of the entity. Allowed to the ones who can create the associated entity.
     */
    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return when (entity.projectEntityType) {
            ProjectEntityType.BUILD -> securityService.isProjectFunctionGranted(entity, BuildCreate::class.java)
            ProjectEntityType.PROMOTION_RUN -> securityService.isProjectFunctionGranted(
                entity,
                PromotionRunCreate::class.java
            )

            ProjectEntityType.VALIDATION_RUN -> securityService.isProjectFunctionGranted(
                entity,
                ValidationRunCreate::class.java
            )

            else -> throw PropertyUnsupportedEntityTypeException(javaClass.getName(), entity.projectEntityType)
        }
    }

    /**
     * Everybody can see the property value.
     */
    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return true
    }

    override fun forStorage(value: JenkinsBuildProperty): JsonNode {
        return mapOf(
            "configuration" to value.configuration.name,
            "job" to value.job,
            "build" to value.build,
        ).asJson()
    }

    override fun fromClient(node: JsonNode): JenkinsBuildProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): JenkinsBuildProperty {
        val configurationName = node.path("configuration").asText()
        val job = node.path("job").asText()
        val build = node.path("build").asInt()
        // Looks the configuration up
        val configuration = loadConfiguration(configurationName)
        // Validates the job name
        validateNotBlank(job, "The Jenkins Job name must not be empty")
        // OK
        return JenkinsBuildProperty(
            configuration,
            job,
            build
        )
    }

    override fun replaceValue(
        value: JenkinsBuildProperty,
        replacementFunction: (String) -> String,
    ): JenkinsBuildProperty {
        return JenkinsBuildProperty(
            replaceConfiguration(value.configuration, replacementFunction),
            replacementFunction(value.job),
            value.build
        )
    }
}
