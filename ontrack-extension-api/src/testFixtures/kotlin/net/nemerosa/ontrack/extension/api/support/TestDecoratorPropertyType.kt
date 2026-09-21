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
import org.springframework.stereotype.Component
import java.util.*

@Component
class TestDecoratorPropertyType(
    extensionFeature: TestExtensionFeature
) : AbstractPropertyType<TestDecorationData>(extensionFeature) {

    override val name: String = "Decorator value"

    override val description: String = "Value."

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.allOf(ProjectEntityType::class.java)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(TestDecorationData::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, ProjectEdit::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): TestDecorationData {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): TestDecorationData {
        return parse(node, TestDecorationData::class)
    }

    override fun replaceValue(
        value: TestDecorationData,
        replacementFunction: (String) -> String
    ): TestDecorationData {
        return TestDecorationData(
            replacementFunction(value.value),
            value.isValid
        )
    }
}
