package net.nemerosa.ontrack.extension.scm.mock

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "ontrack.config.extension.scm.mock",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class MockSCMProjectPropertyType(
    extensionFeature: SCMExtensionFeature,
): AbstractPropertyType<MockSCMProjectProperty>(extensionFeature) {

    override val name: String = "Mock SCM"

    override val description: String = "Mock SCM used for testing only"

    override val supportedEntityTypes: Set<ProjectEntityType> = setOf(ProjectEntityType.PROJECT)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(MockSCMProjectProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): MockSCMProjectProperty = node.parse()

    override fun fromStorage(node: JsonNode): MockSCMProjectProperty = node.parse()

    override fun replaceValue(
        value: MockSCMProjectProperty,
        replacementFunction: (String) -> String,
    ): MockSCMProjectProperty = value

}