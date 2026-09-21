package net.nemerosa.ontrack.extension.api.support

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.ProjectEdit
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.support.ConfigurationPropertyType
import org.springframework.stereotype.Component
import java.util.*

@Component
class TestPropertyType(
    extensionFeature: TestExtensionFeature
) : AbstractPropertyType<TestProperty>(extensionFeature),
    ConfigurationPropertyType<TestConfiguration, TestProperty> {

    override val name: String = "Configuration value"

    override val description: String = "Value."

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.allOf(ProjectEntityType::class.java)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(TestProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return securityService.isProjectFunctionGranted(entity, ProjectEdit::class.java)
    }

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return true
    }

    override fun forStorage(value: TestProperty): JsonNode =
        mapOf(
            TestProperty::configuration.name to value.configuration.name,
            TestProperty::value.name to value.value,
        ).asJson()

    override fun fromClient(node: JsonNode): TestProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): TestProperty {
        return TestProperty(
            TestConfiguration.config(node.getRequiredTextField(TestProperty::configuration.name)),
            node.getRequiredTextField(TestProperty::value.name),
        )
    }

    override fun replaceValue(value: TestProperty, replacementFunction: (String) -> String): TestProperty {
        return TestProperty(
            value.configuration,
            replacementFunction(value.value)
        )
    }
}