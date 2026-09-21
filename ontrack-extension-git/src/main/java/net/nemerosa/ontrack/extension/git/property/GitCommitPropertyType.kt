package net.nemerosa.ontrack.extension.git.property

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.git.GitExtensionFeature
import net.nemerosa.ontrack.extension.git.service.GitService
import net.nemerosa.ontrack.extension.scm.index.SCMBuildCommitIndexService
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.getRequiredTextField
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.BuildCreate
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertySearchArguments
import org.springframework.stereotype.Component
import java.util.*

@Component
class GitCommitPropertyType(
    extensionFeature: GitExtensionFeature,
    private val gitService: GitService,
    private val scmBuildCommitIndexService: SCMBuildCommitIndexService,
) : AbstractPropertyType<GitCommitProperty>(extensionFeature) {

    override val name: String = "Git commit"

    override val description: String = "Git commit"

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.of(ProjectEntityType.BUILD)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(GitCommitProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return securityService.isProjectFunctionGranted(entity, BuildCreate::class.java)
    }

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return true
    }

    override fun fromClient(node: JsonNode): GitCommitProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): GitCommitProperty {
        return GitCommitProperty(
            commit = node.getRequiredTextField("commit"),
        )
    }

    override fun replaceValue(
        value: GitCommitProperty,
        replacementFunction: (String) -> String
    ): GitCommitProperty {
        // A commit is immutable...
        return value
    }

    /**
     * Makes sure to reindex the build
     */
    override fun onPropertyChanged(entity: ProjectEntity, value: GitCommitProperty) {
        if (entity is Build) {
            scmBuildCommitIndexService.indexBuildCommit(entity, value.commit)
            gitService.collectIndexableGitCommitForBuild(entity)
        }
    }

    /**
     * Search criteria
     */
    override fun getSearchArguments(token: String): PropertySearchArguments? =
        getGitCommitSearchArguments(token)

    companion object {
        fun getGitCommitSearchArguments(token: String): PropertySearchArguments? =
            if (token.isNotBlank()) {
                PropertySearchArguments(
                    jsonContext = null,
                    jsonCriteria = "pp.json->>'commit' = :token",
                    criteriaParams = mapOf("token" to token)
                )
            } else {
                null
            }
    }
}
