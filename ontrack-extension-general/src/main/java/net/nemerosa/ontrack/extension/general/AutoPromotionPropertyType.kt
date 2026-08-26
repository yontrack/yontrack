package net.nemerosa.ontrack.extension.general

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getBooleanField
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.model.json.schema.JsonEmptyType
import net.nemerosa.ontrack.model.json.schema.JsonSchemaIgnore
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Component
@JsonSchemaIgnore
class AutoPromotionPropertyType(
    extensionFeature: GeneralExtensionFeature,
    private val structureService: StructureService
) : AbstractPropertyType<AutoPromotionProperty>(extensionFeature) {

    override val name: String = "Auto promotion"

    override val description: String =
        "Allows a promotion level to be granted on a build as soon as a list of validation stamps and/or other promotions has been passed"

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.of(ProjectEntityType.PROMOTION_LEVEL)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType = JsonEmptyType.INSTANCE

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): AutoPromotionProperty {
        return loadAutoPromotionProperty(node)
    }

    private fun loadAutoPromotionProperty(node: JsonNode): AutoPromotionProperty {
        // Backward compatibility (before 2.14)
        if (node.isArray) {
            return AutoPromotionProperty(
                validationStamps = readValidationStamps(node),
                include = "",
                exclude = "",
                promotionLevels = emptyList(),
                autoRevoke = false,
            )
        } else {
            val validationStamps = node.path("validationStamps")
            val validationStampList = readValidationStamps(validationStamps)
            val promotionLevels = node.path("promotionLevels")
            val promotionLevelList = readPromotionLevels(promotionLevels)
            return AutoPromotionProperty(
                validationStamps = validationStampList,
                include = node.getTextField("include") ?: "",
                exclude = node.getTextField("exclude") ?: "",
                promotionLevels = promotionLevelList,
                autoRevoke = node.getBooleanField("autoRevoke") ?: false,
            )
        }
    }

    private fun readValidationStamps(validationStampIds: JsonNode?): List<ValidationStamp> =
        if (validationStampIds != null && validationStampIds.isArray) {
            validationStampIds.map { idNode ->
                val id = idNode.asInt()
                structureService.getValidationStamp(ID.of(id))
            }
        } else {
            emptyList()
        }

    private fun readPromotionLevels(promotionLevelIds: JsonNode?): List<PromotionLevel> =
        if (promotionLevelIds != null && promotionLevelIds.isArray) {
            promotionLevelIds.map { idNode ->
                val id = idNode.asInt()
                structureService.getPromotionLevel(ID.of(id))
            }
        } else {
            emptyList()
        }

    override fun copy(
        sourceEntity: ProjectEntity,
        value: AutoPromotionProperty,
        targetEntity: ProjectEntity,
        replacementFn: (String) -> String
    ): AutoPromotionProperty {
        val targetPromotionLevel = targetEntity as PromotionLevel
        return AutoPromotionProperty(
            value.validationStamps
                .mapNotNull { vs ->
                    structureService.findValidationStampByName(
                        targetPromotionLevel.branch.project.name,
                        targetPromotionLevel.branch.name,
                        vs.name
                    ).getOrNull()
                },
            value.include,
            value.exclude,
            value.promotionLevels
                .mapNotNull { pl ->
                    structureService.findPromotionLevelByName(
                        targetPromotionLevel.branch.project.name,
                        targetPromotionLevel.branch.name,
                        pl.name
                    ).getOrNull()
                },
            value.autoRevoke,
        )
    }

    /**
     * As a list of validation stamp IDs
     */
    override fun forStorage(value: AutoPromotionProperty): JsonNode {
        return mapOf(
            "validationStamps" to value.validationStamps.map { it.id() },
            "include" to value.include,
            "exclude" to value.exclude,
            "promotionLevels" to value.promotionLevels.map { it.id() },
            "autoRevoke" to value.autoRevoke,
        ).asJson()
    }

    override fun fromStorage(node: JsonNode): AutoPromotionProperty {
        return loadAutoPromotionProperty(node)
    }

    override fun replaceValue(
        value: AutoPromotionProperty,
        replacementFunction: (String) -> String
    ): AutoPromotionProperty {
        return value
    }
}
