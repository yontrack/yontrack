package net.nemerosa.ontrack.extension.gitlab.config

import net.nemerosa.ontrack.extension.config.ci.engine.CIEngine
import net.nemerosa.ontrack.extension.config.model.BranchConfiguration
import net.nemerosa.ontrack.extension.config.model.BuildConfiguration
import net.nemerosa.ontrack.extension.config.model.ProjectConfiguration
import net.nemerosa.ontrack.extension.config.model.getActualIssueServiceIdentifier
import net.nemerosa.ontrack.extension.config.scm.AbstractSCMEngine
import net.nemerosa.ontrack.extension.config.scm.SCMEngineNoURLException
import net.nemerosa.ontrack.extension.git.config.GitSCMEngineHelper
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.property.GitLabProjectConfigurationProperty
import net.nemerosa.ontrack.extension.gitlab.property.GitLabProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component

/**
 * SCM engine for GitLab projects.
 *
 * GitLab runs on gitlab.com and self-managed alike, so - unlike Bitbucket Cloud, whose engine matches a
 * hardcoded `bitbucket.org` - the SCM URL is matched against the URLs of the **configured** instances,
 * as GitHub's engine does. See [GitLabSCMUrl] for the matching itself.
 *
 * The project path is everything after the host, subgroups included, which is exactly what the project
 * property's single `repository` field holds.
 */
@Component
class GitLabSCMEngine(
    propertyService: PropertyService,
    private val gitLabConfigurationService: GitLabConfigurationService,
    private val gitSCMEngineHelper: GitSCMEngineHelper,
) : AbstractSCMEngine(
    propertyService = propertyService,
    name = "gitlab",
) {

    override fun matchesUrl(scmUrl: String): Boolean =
        findConfigurationsByURL(scmUrl).isNotEmpty()

    override fun configureProject(
        project: Project,
        configuration: ProjectConfiguration,
        env: Map<String, String>,
        projectName: String,
        ciEngine: CIEngine,
    ) {
        val scmUrl = ciEngine.getScmUrl(env) ?: throw SCMEngineNoURLException()
        val gitLabConfig = getGitLabConfiguration(configuration.scmConfig, scmUrl)
        val repository = getGitLabRepository(gitLabConfig, scmUrl)
        val projectConfig = GitLabProjectConfigurationProperty(
            configuration = gitLabConfig,
            repository = repository,
            indexationInterval = configuration.scmIndexationInterval ?: 0,
            issueServiceConfigurationIdentifier = configuration.getActualIssueServiceIdentifier(env)
                ?.toRepresentation(),
        )
        val existingConfig =
            propertyService.getPropertyValue(project, GitLabProjectConfigurationPropertyType::class.java)
        if (existingConfig == null || !sameProperty(existingConfig, projectConfig)) {
            propertyService.editProperty(
                project,
                GitLabProjectConfigurationPropertyType::class.java,
                projectConfig
            )
        }
    }

    /**
     * The property is not a data class, so compares it field by field.
     */
    private fun sameProperty(
        a: GitLabProjectConfigurationProperty,
        b: GitLabProjectConfigurationProperty,
    ) = a.configuration.name == b.configuration.name &&
            a.repository == b.repository &&
            a.indexationInterval == b.indexationInterval &&
            a.issueServiceConfigurationIdentifier == b.issueServiceConfigurationIdentifier

    /**
     * The configuration named by `scmConfig` when there is one, the one matching the SCM URL otherwise.
     */
    internal fun getGitLabConfiguration(scmConfig: String?, scmUrl: String): GitLabConfiguration =
        if (!scmConfig.isNullOrBlank()) {
            gitLabConfigurationService.getConfiguration(scmConfig)
        } else {
            val configurations = findConfigurationsByURL(scmUrl)
            when (configurations.size) {
                0 -> throw GitLabSCMNoConfigException(scmUrl)
                1 -> configurations.first()
                else -> throw GitLabSCMAmbiguousConfigException(scmUrl, configurations.map { it.name })
            }
        }

    /**
     * Project path of the SCM URL, relative to the instance of the given configuration.
     */
    internal fun getGitLabRepository(configuration: GitLabConfiguration, scmUrl: String): String =
        GitLabSCMUrl.projectPath(configuration.url, scmUrl)
            ?: throw GitLabSCMRepositoryNotDetectedException(scmUrl)

    private fun findConfigurationsByURL(scmUrl: String): List<GitLabConfiguration> =
        gitLabConfigurationService.configurations.filter {
            GitLabSCMUrl.projectPath(it.url, scmUrl) != null
        }

    override fun configureBranch(
        branch: Branch,
        configuration: BranchConfiguration,
        env: Map<String, String>,
        scmBranch: String,
    ) {
        gitSCMEngineHelper.configureBranch(
            branch = branch,
            scmBranch = scmBranch,
        )
    }

    override fun configureBuild(
        build: Build,
        configuration: BuildConfiguration,
        env: Map<String, String>,
        ciEngine: CIEngine,
    ) {
        val commit = ciEngine.getScmRevision(env)
        if (!commit.isNullOrBlank()) {
            gitSCMEngineHelper.configureBuild(
                build = build,
                commit = commit,
            )
        }
    }
}
