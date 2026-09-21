package net.nemerosa.ontrack.extension.api.support

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.ProjectEdit
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertySearchArguments
import org.springframework.stereotype.Component
import java.util.*

@Component
class TestSimplePropertyType(
    extensionFeature: TestExtensionFeature
) : AbstractPropertyType<TestSimpleProperty>(extensionFeature) {

    override val name: String = "Simple value"

    override val description: String = "Value."

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.allOf(ProjectEntityType::class.java)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(TestSimpleProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, ProjectEdit::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): TestSimpleProperty = fromStorage(node)

    override fun fromStorage(node: JsonNode): TestSimpleProperty = parse(node, TestSimpleProperty::class)

    override fun replaceValue(
        value: TestSimpleProperty,
        replacementFunction: (String) -> String
    ): TestSimpleProperty {
        return TestSimpleProperty(
            replacementFunction(value.value)
        )
    }

    override fun getSearchArguments(token: String): PropertySearchArguments? {
        return PropertySearchArguments(
            null,
            "pp.json->>'value' like :value",
            Collections.singletonMap("value", "%$token%")
        )
    }

    override fun containsValue(value: TestSimpleProperty, propertyValue: String): Boolean =
        value.value.contains(propertyValue, ignoreCase = true)
}
