package net.nemerosa.ontrack.extension.general

import tools.jackson.databind.JsonNode
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
class PreviousPromotionConditionPropertyType(
        extensionFeature: GeneralExtensionFeature
) : AbstractPropertyType<PreviousPromotionConditionProperty>(extensionFeature) {

    override val name: String = "Previous promotion condition"

    override val description: String =
            "Makes a promotion conditional based on the fact that a previous promotion has been granted."

    /**
     * Project, branch and promotion levels are supported.
     */
    override val supportedEntityTypes: Set<ProjectEntityType> =
            setOf(
                    ProjectEntityType.PROJECT,
                    ProjectEntityType.BRANCH,
                    ProjectEntityType.PROMOTION_LEVEL
            )

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(PreviousPromotionConditionProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
            securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean =
            true

    override fun fromClient(node: JsonNode): PreviousPromotionConditionProperty = fromStorage(node)

    override fun fromStorage(node: JsonNode): PreviousPromotionConditionProperty = node.parse()

    override fun replaceValue(value: PreviousPromotionConditionProperty, replacementFunction: (String) -> String): PreviousPromotionConditionProperty =
            value
}