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
import net.nemerosa.ontrack.model.structure.PropertySearchArguments
import org.springframework.stereotype.Component
import java.util.*

@Component
class MessagePropertyType(extensionFeature: GeneralExtensionFeature) :
    AbstractPropertyType<MessageProperty>(extensionFeature) {
    override val name: String = "Message"

    override val description: String =
        "Associates an arbitrary message (and its type) to an entity. Will be displayed as a decorator in the UI."

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.allOf(ProjectEntityType::class.java)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(MessageProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)
    }

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return true
    }

    override fun fromClient(node: JsonNode): MessageProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): MessageProperty {
        return node.parse()
    }

    @Deprecated("Will be removed in V5")
    override fun replaceValue(
        value: MessageProperty,
        replacementFunction: (String) -> String
    ): MessageProperty {
        return MessageProperty(
            value.type,
            replacementFunction(value.text)
        )
    }

    override fun getSearchArguments(token: String): PropertySearchArguments? {
        return PropertySearchArguments(
            jsonContext = null,
            jsonCriteria = "pp.json->>'text' ilike :text",
            criteriaParams = Collections.singletonMap("text", "%$token%")
        )
    }

    override fun containsValue(value: MessageProperty, propertyValue: String): Boolean {
        return value.text.contains(propertyValue, ignoreCase = true)
    }
}
