package net.nemerosa.ontrack.extension.general

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.extension.ValidationStampPropertyType
import net.nemerosa.ontrack.model.json.schema.JsonEmptyType
import net.nemerosa.ontrack.model.json.schema.JsonSchemaIgnore
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.PredefinedValidationStampService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component
import java.util.*

@Component
@JsonSchemaIgnore
class AutoValidationStampPropertyType(
    extensionFeature: GeneralExtensionFeature,
    private val predefinedValidationStampService: PredefinedValidationStampService,
    private val securityService: SecurityService,
    private val structureService: StructureService
) : AbstractPropertyType<AutoValidationStampProperty>(extensionFeature),
    ValidationStampPropertyType<AutoValidationStampProperty> {

    override fun getOrCreateValidationStamp(
        value: AutoValidationStampProperty,
        branch: Branch,
        validationStampName: String
    ): Optional<ValidationStamp> {
        if (value.isAutoCreate) {
            val oPredefinedValidationStamp =
                predefinedValidationStampService.findPredefinedValidationStampByName(validationStampName)
            if (oPredefinedValidationStamp != null) {
                // Creates the validation stamp
                return Optional.of<ValidationStamp>(
                    securityService.asAdmin {
                        structureService.newValidationStampFromPredefined(
                            branch,
                            oPredefinedValidationStamp
                        )
                    }
                )
            } else if (value.isAutoCreateIfNotPredefined) {
                // Creates a validation stamp even without a predefined one
                return Optional.of<ValidationStamp>(
                    securityService.asAdmin {
                        structureService.newValidationStamp(
                            ValidationStamp.of(
                                branch,
                                NameDescription.nd(validationStampName, "Validation automatically created on demand.")
                            )
                        )
                    }
                )
            }
        }
        return Optional.empty()
    }

    override val name: String = "Auto validation stamps"

    override val description: String =
        "If set, this property allows validation stamps to be created automatically from predefined validation stamps"

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.of(ProjectEntityType.PROJECT)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType = JsonEmptyType.INSTANCE

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)
    }

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return true
    }

    override fun fromClient(node: JsonNode): AutoValidationStampProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): AutoValidationStampProperty =
        node.parse()

    override fun replaceValue(
        value: AutoValidationStampProperty,
        replacementFunction: (String) -> String
    ): AutoValidationStampProperty {
        return value
    }
}
