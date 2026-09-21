package net.nemerosa.ontrack.extension.general

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertySearchArguments
import net.nemerosa.ontrack.model.support.NameValue
import org.apache.commons.lang3.StringUtils
import org.springframework.stereotype.Component
import java.util.*

@Component
class LinkPropertyType(
        extensionFeature: GeneralExtensionFeature
) : AbstractPropertyType<LinkProperty>(extensionFeature) {

    override val name: String = "Links"

    override val description: String = "List of links."

    override val supportedEntityTypes: Set<ProjectEntityType> =
            EnumSet.allOf(ProjectEntityType::class.java)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(LinkProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
            securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): LinkProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): LinkProperty {
        return parse(node, LinkProperty::class)
    }

    override fun replaceValue(value: LinkProperty, replacementFunction: (String) -> String): LinkProperty {
        return LinkProperty(
                value.links.map { nv ->
                    NameValue(nv.name, replacementFunction(nv.value))
                }
        )
    }

    override fun getSearchArguments(token: String): PropertySearchArguments? {
        return PropertySearchArguments(
                jsonContext = "jsonb_array_elements(pp.json->'links') as link",
                jsonCriteria = "link->>'value' ilike :value",
                criteriaParams = mapOf(
                        "value" to "%$token%"
                )
        )
    }

    override fun containsValue(value: LinkProperty, propertyValue: String): Boolean =
            value.links.map {
                it.value
            }.any {
                StringUtils.containsIgnoreCase(it, propertyValue)
            }
}
