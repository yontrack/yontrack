package net.nemerosa.ontrack.extension.github

import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClientFactory
import net.nemerosa.ontrack.extension.github.model.GitHubIssue
import net.nemerosa.ontrack.extension.github.model.GitHubLabel
import net.nemerosa.ontrack.extension.github.property.GitHubGitConfiguration
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService
import net.nemerosa.ontrack.extension.github.service.GitHubIssueServiceConfiguration
import net.nemerosa.ontrack.extension.issues.IssueRepositoryContext
import net.nemerosa.ontrack.extension.issues.model.Issue
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfiguration
import net.nemerosa.ontrack.extension.issues.support.AbstractIssueServiceExtension
import net.nemerosa.ontrack.extension.issues.support.IssueServiceUtils
import net.nemerosa.ontrack.model.support.MessageAnnotation.Companion.of
import net.nemerosa.ontrack.model.support.MessageAnnotator
import net.nemerosa.ontrack.model.support.RegexMessageAnnotator
import org.springframework.stereotype.Component
import java.util.regex.Pattern
import java.util.stream.Collectors

@Component
class GitHubIssueServiceExtension(
    extensionFeature: GitHubExtensionFeature,
    private val configurationService: GitHubConfigurationService,
    private val gitHubClientFactory: OntrackGitHubClientFactory,
) : AbstractIssueServiceExtension(
    extensionFeature,
    GITHUB_SERVICE_ID,
    "GitHub",
) {

    /**
     * The GitHub configurations are not selectable and this method returns an empty list.
     */
    override fun getConfigurationList(): List<IssueServiceConfiguration> {
        return emptyList()
    }

    /**
     * A GitHub configuration name
     *
     * @param name Name of the configuration and repository.
     * @return Wrapper for the GitHub issue service.
     * @see net.nemerosa.ontrack.extension.github.property.GitHubGitConfiguration
     */
    override fun getConfigurationByName(name: String): IssueServiceConfiguration? {
        // Parsing of the name
        val tokens = name.split(GitHubGitConfiguration.CONFIGURATION_REPOSITORY_SEPARATOR)
        check(tokens.size == 2) { "The GitHub issue configuration identifier name is expected using configuration:repository as a format" }
        val configuration = tokens[0]
        val repository = tokens[1]
        return GitHubIssueServiceConfiguration(
            configurationService.getConfiguration(configuration),
            repository
        )
    }

    fun validIssueToken(token: String?): Boolean {
        return Pattern.matches(GITHUB_ISSUE_PATTERN, token)
    }

    override fun extractIssueKeysFromMessage(
        issueServiceConfiguration: IssueServiceConfiguration,
        message: String?
    ): Set<String> =
        IssueServiceUtils.extractHashIssueKeys(message)

    override fun getMessageAnnotator(issueServiceConfiguration: IssueServiceConfiguration): MessageAnnotator {
        val configuration = issueServiceConfiguration as GitHubIssueServiceConfiguration
        return RegexMessageAnnotator(
            GITHUB_ISSUE_PATTERN.toRegex(),
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
        val configuration = issueServiceConfiguration as GitHubIssueServiceConfiguration
        val client = gitHubClientFactory.create(
            configuration.configuration
        )
        return client.getIssue(
            configuration.repository,
            getIssueId(issueKey)
        )
    }

    override fun getIssueId(issueServiceConfiguration: IssueServiceConfiguration, token: String?): String? {
        return if (token != null && (token.toIntOrNull() != null || validIssueToken(token))) {
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

    fun getIssueId(token: String): Int {
        return token.trimStart('#').toInt(10)
    }

    override fun getIssueTypes(issueServiceConfiguration: IssueServiceConfiguration, issue: Issue): Set<String> {
        val gitHubIssue = issue as GitHubIssue
        return gitHubIssue.labels.stream().map(GitHubLabel::name).collect(Collectors.toSet())
    }

    override fun getLastCommit(
        issueServiceConfiguration: IssueServiceConfiguration,
        repositoryContext: IssueRepositoryContext,
        key: String
    ): String? {
        val numericKey = getIssueId(key)
        val configuration = issueServiceConfiguration as GitHubIssueServiceConfiguration
        val client = gitHubClientFactory.create(
            configuration.configuration
        )
        return client.getIssueLastCommit(configuration.repository, numericKey)
    }

    companion object {
        const val GITHUB_SERVICE_ID: String = "github"
        const val GITHUB_ISSUE_PATTERN: String = "(#(\\d+))"
    }
}
