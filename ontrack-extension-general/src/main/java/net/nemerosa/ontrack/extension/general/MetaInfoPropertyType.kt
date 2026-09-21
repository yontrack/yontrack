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
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.Strings
import org.springframework.stereotype.Component
import java.util.*

@Component
class MetaInfoPropertyType(
    extensionFeature: GeneralExtensionFeature,
) : AbstractPropertyType<MetaInfoProperty>(extensionFeature) {

    override val name: String = "Meta information"

    override val description: String = "List of meta information properties"

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.allOf(ProjectEntityType::class.java)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(MetaInfoProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return securityService.isProjectFunctionGranted(entity, ProjectConfig::class.java)
    }

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): MetaInfoProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): MetaInfoProperty {
        return parse(node, MetaInfoProperty::class)
    }

    override fun containsValue(value: MetaInfoProperty, propertyValue: String): Boolean {
        val pos = Strings.CS.indexOf(propertyValue, ":")
        return if (pos > 0) {
            val entryValue = StringUtils.substringAfter(propertyValue, ":")
            val name = StringUtils.substringBefore(propertyValue, ":")
            value.matchNameValue(name, entryValue)
        } else {
            false
        }
    }

    override fun replaceValue(
        value: MetaInfoProperty,
        replacementFunction: (String) -> String
    ): MetaInfoProperty {
        return MetaInfoProperty(
            value.items
                .map { item ->
                    MetaInfoPropertyItem(
                        item.name,
                        item.value?.run { replacementFunction(this) },
                        item.link?.run { replacementFunction(this) },
                        item.category?.run { replacementFunction(this) }
                    )
                }
        )
    }

    override fun getSearchArguments(token: String): PropertySearchArguments? {
        val (name, value, _, category) = MetaInfoPropertyItem.parse(token)
        // If no name, no search is done
        if (name.isBlank()) {
            return null
        }
        // Criteria
        val criteria = mutableListOf<String>()
        val params = mutableMapOf<String, String>()
        // Name
        criteria += "item->>'name' = :name"
        params["name"] = name
        // Value
        if (value != null) {
            criteria += "item->>'value' ilike :value"
            params["value"] = value.toValuePattern()
        }
        // Category
        if (category != null) {
            if (category.isBlank()) {
                // Explicitly looking for names without categories
                criteria += "item->>'category' is null"
            } else {
                criteria += "item->>'category' = :category"
                params["category"] = category
            }
        }
        // OK
        return PropertySearchArguments(
            jsonContext = "jsonb_array_elements(pp.json->'items') as item",
            jsonCriteria = criteria.joinToString(" and "),
            criteriaParams = params,
        )
    }

    private fun String.toValuePattern(): String {
        return this.replace("*", "%")
    }

}
