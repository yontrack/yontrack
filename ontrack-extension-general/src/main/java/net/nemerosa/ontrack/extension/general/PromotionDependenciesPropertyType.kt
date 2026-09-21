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

/**
 * Definition of the "Promotion dependencies" property type.
 */
@Component
class PromotionDependenciesPropertyType(
        extensionFeature: GeneralExtensionFeature
) : AbstractPropertyType<PromotionDependenciesProperty>(extensionFeature) {

    override val name: String = "Promotion dependencies"

    override val description: String =
            "List of promotions a promotion depends on before being applied."

    /**
     * Only for promotions
     */
    override val supportedEntityTypes: Set<ProjectEntityType> =
            setOf(ProjectEntityType.PROMOTION_LEVEL)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(PromotionDependenciesProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
            securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean =
            true

    override fun fromClient(node: JsonNode): PromotionDependenciesProperty = fromStorage(node)

    override fun fromStorage(node: JsonNode): PromotionDependenciesProperty = node.parse()

    override fun replaceValue(value: PromotionDependenciesProperty, replacementFunction: (String) -> String): PromotionDependenciesProperty =
            value
}