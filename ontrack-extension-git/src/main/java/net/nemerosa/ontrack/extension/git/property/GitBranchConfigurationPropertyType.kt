package net.nemerosa.ontrack.extension.git.property

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.git.GitBranchSearchIndexer
import net.nemerosa.ontrack.extension.git.GitExtensionFeature
import net.nemerosa.ontrack.extension.git.model.BuildGitCommitLink
import net.nemerosa.ontrack.extension.git.model.ConfiguredBuildGitCommitLink
import net.nemerosa.ontrack.extension.git.model.IndexableBuildGitCommitLink
import net.nemerosa.ontrack.extension.git.service.BuildGitCommitLinkService
import net.nemerosa.ontrack.extension.git.service.GitService
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.getBooleanField
import net.nemerosa.ontrack.json.getIntField
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component
import java.util.*
import java.util.function.Function

@Component
class GitBranchConfigurationPropertyType(
    extensionFeature: GitExtensionFeature,
    private val buildGitCommitLinkService: BuildGitCommitLinkService,
    private val gitService: GitService,
    private val gitBranchSearchIndexer: GitBranchSearchIndexer
) : AbstractPropertyType<GitBranchConfigurationProperty>(extensionFeature) {

    override val name: String = "Git branch"

    override val description: String = "Git branch"

    override val supportedEntityTypes: Set<ProjectEntityType> = EnumSet.of(ProjectEntityType.BRANCH)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(GitBranchConfigurationProperty::class)

    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean {
        return securityService.isProjectFunctionGranted(entity.projectId(), ProjectConfig::class.java)
    }

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): GitBranchConfigurationProperty {
        return fromStorage(node)
    }

    override fun fromStorage(node: JsonNode): GitBranchConfigurationProperty {
        val configuredBuildGitCommitLink =
            if (node.has("buildCommitLink")) {
                val linkNode = node["buildCommitLink"]
                parseBuildCommitLink<Any>(linkNode)
            } else {
                null
            }
        val indexationAvailable =
            configuredBuildGitCommitLink != null && configuredBuildGitCommitLink.link is IndexableBuildGitCommitLink<*>
        return GitBranchConfigurationProperty(
            branch = node.getTextField("branch") ?: "main",
            buildCommitLink = configuredBuildGitCommitLink?.toServiceConfiguration(),
            override = indexationAvailable && (node.getBooleanField("override") ?: false),
            buildTagInterval = if (indexationAvailable) (node.getIntField("buildTagInterval") ?: 0) else 0
        )
    }

    private fun <T> parseBuildCommitLink(linkNode: JsonNode): ConfiguredBuildGitCommitLink<T>? {
        if (linkNode.isNull) {
            return null
        }
        val linkId = linkNode.getTextField("id") ?: return null
        // Gets the link data
        val linkDataNode = linkNode["data"]
        // Gets the link
        @Suppress("UNCHECKED_CAST")
        val link = buildGitCommitLinkService.getLink(linkId) as BuildGitCommitLink<T>
        // Parses the data (for validation)
        val linkData = link.parseData(linkDataNode)
        // OK
        return ConfiguredBuildGitCommitLink(
            link,
            linkData
        )
    }

    override fun replaceValue(
        value: GitBranchConfigurationProperty,
        replacementFunction: (String) -> String
    ): GitBranchConfigurationProperty {
        return GitBranchConfigurationProperty(
            replacementFunction(value.branch),
            if (value.buildCommitLink != null) replaceBuildCommitLink<Any>(
                value.buildCommitLink,
                replacementFunction
            ) else null,
            value.override,
            value.buildTagInterval
        )
    }

    private fun <T> replaceBuildCommitLink(
        configuration: ServiceConfiguration,
        replacementFunction: Function<String, String>
    ): ServiceConfiguration {
        val linkId = configuration.id

        @Suppress("UNCHECKED_CAST")
        val link = buildGitCommitLinkService.getLink(linkId) as BuildGitCommitLink<T>
        val linkData = link.parseData(configuration.data)
        val clonedData = link.clone(linkData) { replacementFunction.apply(it) }
        val node = link.toJson(clonedData)
        return ServiceConfiguration(
            linkId,
            node
        )
    }

    override fun onPropertyChanged(entity: ProjectEntity, value: GitBranchConfigurationProperty) {
        if (entity is Branch) {
            gitService.scheduleGitBuildSync(entity, value)
            gitBranchSearchIndexer.onGitBranchChanged(entity, value)
        }
    }

    override fun onPropertyDeleted(entity: ProjectEntity, oldValue: GitBranchConfigurationProperty) {
        if (entity is Branch) {
            gitService.unscheduleGitBuildSync(entity, oldValue)
            gitBranchSearchIndexer.onGitBranchDeleted(entity)
        }
    }

}