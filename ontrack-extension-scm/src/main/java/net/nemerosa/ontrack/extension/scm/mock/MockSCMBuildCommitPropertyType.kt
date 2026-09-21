package net.nemerosa.ontrack.extension.scm.mock

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.extension.scm.index.SCMBuildCommitIndexService
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Build
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
class MockSCMBuildCommitPropertyType(
    extensionFeature: SCMExtensionFeature,
    private val scmBuildCommitIndexService: SCMBuildCommitIndexService,
) : AbstractPropertyType<MockSCMBuildCommitProperty>(extensionFeature) {

    override val name: String = "Mock SCM commit"

    override val description: String = "Mock SCM used for testing only"

    override val supportedEntityTypes: Set<ProjectEntityType> = setOf(ProjectEntityType.BUILD)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(MockSCMBuildCommitProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): MockSCMBuildCommitProperty = node.parse()

    override fun fromStorage(node: JsonNode): MockSCMBuildCommitProperty = node.parse()

    /**
     * Makes sure to reindex the build
     */
    override fun onPropertyChanged(entity: ProjectEntity, value: MockSCMBuildCommitProperty) {
        if (entity is Build) {
            scmBuildCommitIndexService.indexBuildCommit(entity, value.id)
        }
    }

    override fun replaceValue(
        value: MockSCMBuildCommitProperty,
        replacementFunction: (String) -> String,
    ): MockSCMBuildCommitProperty = value

}
