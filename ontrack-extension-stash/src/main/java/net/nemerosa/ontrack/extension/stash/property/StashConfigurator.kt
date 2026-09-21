package net.nemerosa.ontrack.extension.stash.property

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.git.model.GitConfiguration
import net.nemerosa.ontrack.extension.git.model.GitConfigurator
import net.nemerosa.ontrack.extension.git.model.GitPullRequest
import net.nemerosa.ontrack.extension.issues.IssueServiceRegistry
import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.extension.support.client.jackson2RestTemplateBuilder
import org.springframework.stereotype.Component
import org.springframework.web.client.getForObject

@Component
class StashConfigurator(
    private val propertyService: PropertyService,
    private val issueServiceRegistry: IssueServiceRegistry,
) : GitConfigurator {

    override fun isProjectConfigured(project: Project): Boolean {
        return propertyService.hasProperty(project, StashProjectConfigurationPropertyType::class.java)
    }

    override fun getConfiguration(project: Project): GitConfiguration? {
        return propertyService.getProperty(project, StashProjectConfigurationPropertyType::class.java)
            .value
            ?.run { getGitConfiguration(this) }
    }

    override fun toPullRequestKey(prId: Int): String = "PR-$prId"

    override fun getPullRequest(configuration: GitConfiguration, id: Int): GitPullRequest? =
        if (configuration is StashGitConfiguration) {
            val restTemplate = jackson2RestTemplateBuilder()
                .rootUri(configuration.configuration.url)
                .basicAuthentication(
                    requireNotNull(configuration.configuration.user) { "Username must not be null" },
                    requireNotNull(configuration.configuration.password) { "Password must not be null" },
                )
                .build()
            val json =
                restTemplate.getForObject<JsonNode>("rest/api/1.0/projects/${configuration.project}/repos/${configuration.repository}/pull-requests/$id")!!
            GitPullRequest(
                id = id,
                key = "PR-$id",
                source = json["fromRef"]["id"].asText(),
                target = json["toRef"]["id"].asText(),
                title = json["title"].asText(),
                status = json["state"].asText(),
                url = "${configuration.configuration.url}/projects/${configuration.project}/repos/${configuration.repository}/pull-requests/$id"
            )
        } else {
            null
        }

    fun getGitConfiguration(property: StashProjectConfigurationProperty): StashGitConfiguration =
        StashGitConfiguration(
            configuration = property.configuration,
            project = property.project,
            repository = property.repository,
            indexationInterval = property.indexationInterval,
            configuredIssueService = getConfiguredIssueService(property),
        )

    private fun getConfiguredIssueService(property: StashProjectConfigurationProperty): ConfiguredIssueService? =
        property.issueServiceConfigurationIdentifier
            ?.takeIf { it.isNotBlank() }
            ?.let { issueServiceRegistry.getConfiguredIssueService(it) }

}