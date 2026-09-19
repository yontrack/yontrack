package net.nemerosa.ontrack.extension.gitlab

import net.nemerosa.ontrack.extension.gitlab.client.GitLabClientFactory
import net.nemerosa.ontrack.extension.gitlab.model.GitLabIssueServiceConfiguration
import net.nemerosa.ontrack.extension.gitlab.model.GitLabIssueWrapper
import net.nemerosa.ontrack.extension.gitlab.property.GitLabGitConfiguration
import net.nemerosa.ontrack.extension.gitlab.property.GitLabProjectConfigurationProperty
import net.nemerosa.ontrack.extension.gitlab.property.GitLabProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import net.nemerosa.ontrack.extension.issues.IssueRepositoryContext
import net.nemerosa.ontrack.extension.issues.model.Issue
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfiguration
import net.nemerosa.ontrack.extension.issues.support.AbstractIssueServiceExtension
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.forEachEntityWithProperty
import net.nemerosa.ontrack.model.support.MessageAnnotation.Companion.of
import net.nemerosa.ontrack.model.support.MessageAnnotator
import net.nemerosa.ontrack.model.support.RegexMessageAnnotator
import org.apache.commons.lang3.StringUtils
import org.springframework.stereotype.Component
import java.util.regex.Pattern

@Component
class GitLabIssueServiceExtension(
    extensionFeature: GitLabExtensionFeature,
    private val configurationService: GitLabConfigurationService,
    private val gitLabClientFactory: GitLabClientFactory,
    private val propertyService: PropertyService,
) : AbstractIssueServiceExtension(
    extensionFeature,
    GITLAB_SERVICE_ID,
    "GitLab",
) {
    /**
     * The GitLab projects Yontrack already knows about, each usable as an issue service.
     *
     * An issue service configuration is a *configuration and a project path*, and nothing but a GitLab
     * project property carries that pair: a configuration on its own names an instance, not a project, and
     * asking the instance for its projects would put a remote call behind every opening of the property
     * form. So the list is the set of `configuration:project` pairs the instance is already configured
     * with, deduplicated - several Yontrack projects may well be following the same GitLab project.
     *
     * A project whose own GitLab property is the one that produced an entry does not need it: it selects
     * `self` instead, which [net.nemerosa.ontrack.extension.gitlab.property.GitLabConfigurator] resolves.
     * The entries are for everybody else - a plain Git project, or a project whose code and issues do not
     * live in the same GitLab project.
     */
    override fun getConfigurationList(): List<IssueServiceConfiguration> {
        val configurations = mutableMapOf<String, GitLabIssueServiceConfiguration>()
        propertyService.forEachEntityWithProperty<GitLabProjectConfigurationPropertyType, GitLabProjectConfigurationProperty> { _, property ->
            val configuration = GitLabIssueServiceConfiguration(
                configuration = property.configuration,
                repository = property.repository,
            )
            configurations[configuration.name] = configuration
        }
        return configurations.values.sortedBy { it.name }
    }

    /**
     * A GitLab configuration name
     *
     * @param name Name of the configuration and repository.
     * @return Wrapper for the GitHub issue service.
     * @see net.nemerosa.ontrack.extension.gitlab.property.GitLabGitConfiguration
     */
    override fun getConfigurationByName(name: String): IssueServiceConfiguration? {
        // Parsing of the name
        val tokens = StringUtils.split(name, GitLabGitConfiguration.CONFIGURATION_REPOSITORY_SEPARATOR)
        check(!(tokens == null || tokens.size != 2)) {
            "The GitLab issue configuration identifier name is expected using configuration:repository as a format"
        }
        val configuration = tokens[0]
        val repository = tokens[1]
        return GitLabIssueServiceConfiguration(
            configurationService.getConfiguration(configuration),
            repository
        )
    }

    private fun validIssueToken(token: String): Boolean {
        return Pattern.matches(GITLAB_ISSUE_PATTERN, token)
    }

    override fun extractIssueKeysFromMessage(
        issueServiceConfiguration: IssueServiceConfiguration,
        message: String?
    ): Set<String> {
        val result: MutableSet<String> = HashSet()
        if (!message.isNullOrBlank()) {
            val matcher = Pattern.compile(GITLAB_ISSUE_PATTERN).matcher(message)
            while (matcher.find()) {
                // Gets the issue
                val issueKey = matcher.group(2)
                // Adds to the result
                result.add(issueKey)
            }
        }
        // OK
        return result
    }

    override fun getMessageAnnotator(issueServiceConfiguration: IssueServiceConfiguration): MessageAnnotator {
        val configuration = issueServiceConfiguration as GitLabIssueServiceConfiguration
        return RegexMessageAnnotator(
            GITLAB_ISSUE_PATTERN.toRegex()
        ) { key: String ->
            of("a")
                .attr(
                    "href",
                    "${configuration.configuration.url}/${configuration.repository}/issues/${key.substring(1)}"
                )
                .text(key)
        }
    }

    override fun getIssue(issueServiceConfiguration: IssueServiceConfiguration, issueKey: String): Issue? {
        val configuration = issueServiceConfiguration as GitLabIssueServiceConfiguration
        val client = gitLabClientFactory.create(configuration.configuration)
        val issue = client.getIssue(
            configuration.repository,
            getIssueId(issueKey)
        ) ?: return null
        return GitLabIssueWrapper(
            gitlabIssue = issue,
            milestoneUrl = issue.milestone?.let { milestone ->
                milestone.web_url ?: "${configuration.configuration.url.trimEnd('/')}/${configuration.repository}/-/milestones/${milestone.iid}"
            },
        )
    }

    override fun getIssueId(issueServiceConfiguration: IssueServiceConfiguration, token: String?): String? {
        return if (token != null && (StringUtils.isNumeric(token) || validIssueToken(token))) {
            getIssueId(token).toString()
        } else {
            null
        }
    }

    override fun getDisplayKey(issueServiceConfiguration: IssueServiceConfiguration, key: String): String {
        return if (key.startsWith("#")) {
            key
        } else {
            "#$key"
        }
    }

    fun getIssueId(token: String?): Int {
        return StringUtils.stripStart(token, "#").toInt(10)
    }

    override fun getIssueTypes(issueServiceConfiguration: IssueServiceConfiguration, issue: Issue): Set<String> {
        val wrapper = issue as GitLabIssueWrapper
        return HashSet(wrapper.labels)
    }

    override fun getLastCommit(
        issueServiceConfiguration: IssueServiceConfiguration,
        repositoryContext: IssueRepositoryContext,
        key: String
    ): String? {
        val configuration = issueServiceConfiguration as GitLabIssueServiceConfiguration
        val client = gitLabClientFactory.create(configuration.configuration)
        return client.getIssueLastCommit(
            configuration.repository,
            getIssueId(key)
        )
    }

    companion object {
        const val GITLAB_SERVICE_ID: String = "gitlab"
        private const val GITLAB_ISSUE_PATTERN = "(#(\\d+))"
    }
}
