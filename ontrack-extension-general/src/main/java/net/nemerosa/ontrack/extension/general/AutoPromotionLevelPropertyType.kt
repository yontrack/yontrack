package net.nemerosa.ontrack.extension.general

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.extension.PromotionLevelPropertyType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.PredefinedPromotionLevelService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component
import java.util.*

@Component
class AutoPromotionLevelPropertyType(
    extensionFeature: GeneralExtensionFeature,
    private val predefinedPromotionLevelService: PredefinedPromotionLevelService,
    private val structureService: StructureService,
    private val securityService: SecurityService
) : AbstractPropertyType<AutoPromotionLevelProperty>(extensionFeature),
    PromotionLevelPropertyType<AutoPromotionLevelProperty> {

    override fun getOrCreatePromotionLevel(
        value: AutoPromotionLevelProperty,
        branch: Branch,
        promotionLevelName: String
    ): Optional<PromotionLevel> {
        if (value.isAutoCreate) {
            val oPredefinedPromotionLevel =
                predefinedPromotionLevelService.findPredefinedPromotionLevelByName(promotionLevelName)
            if (oPredefinedPromotionLevel != null) {
                // Creates the promotion level
                return Optional.of<PromotionLevel>(
                    securityService.asAdmin {
                        structureService.newPromotionLevelFromPredefined(
                            branch,
                            oPredefinedPromotionLevel
                        )
                    }
                )
            }
        }
        return Optional.empty()
    }

    override val name: String = "Auto promotion levels"

    override val description: String =
        "If set, this property allows promotion levels to be created automatically from predefined promotion levels"

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.of(ProjectEntityType.PROJECT)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(AutoPromotionLevelProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)
    }

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return true
    }

    override fun fromClient(node: JsonNode): AutoPromotionLevelProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): AutoPromotionLevelProperty {
        return node.parse()
    }

    override fun replaceValue(
        value: AutoPromotionLevelProperty,
        replacementFunction: (String) -> String
    ): AutoPromotionLevelProperty {
        return value
    }
}
