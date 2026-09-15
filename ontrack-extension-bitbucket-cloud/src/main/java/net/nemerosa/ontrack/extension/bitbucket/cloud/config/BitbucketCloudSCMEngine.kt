package net.nemerosa.ontrack.extension.bitbucket.cloud.config

import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BitbucketCloudProjectConfigurationProperty
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BitbucketCloudProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.config.ci.engine.CIEngine
import net.nemerosa.ontrack.extension.config.model.BranchConfiguration
import net.nemerosa.ontrack.extension.config.model.BuildConfiguration
import net.nemerosa.ontrack.extension.config.model.ProjectConfiguration
import net.nemerosa.ontrack.extension.config.model.getActualIssueServiceIdentifier
import net.nemerosa.ontrack.extension.config.scm.AbstractSCMEngine
import net.nemerosa.ontrack.extension.config.scm.SCMEngineNoURLException
import net.nemerosa.ontrack.extension.git.config.GitSCMEngineHelper
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component

/**
 * SCM engine for Bitbucket Cloud repositories.
 *
 * Unlike GitHub or Bitbucket Server, a Bitbucket Cloud configuration carries no URL, so the
 * configuration cannot be detected from the SCM URL: it's the one named by `scmConfig`, or the
 * only one there is.
 */
@Component
class BitbucketCloudSCMEngine(
    propertyService: PropertyService,
    private val bitbucketCloudConfigurationService: BitbucketCloudConfigurationService,
    private val gitSCMEngineHelper: GitSCMEngineHelper,
) : AbstractSCMEngine(
    propertyService = propertyService,
    name = "bitbucket-cloud",
) {

    override fun matchesUrl(scmUrl: String): Boolean =
        scmUrlRegex.matches(scmUrl)

    override fun configureProject(
        project: Project,
        configuration: ProjectConfiguration,
        env: Map<String, String>,
        projectName: String,
        ciEngine: CIEngine,
    ) {
        val scmUrl = ciEngine.getScmUrl(env) ?: throw SCMEngineNoURLException()
        val (workspace, repository) = getWorkspaceAndRepository(scmUrl)
        val bitbucketCloudConfig = getBitbucketCloudConfiguration(configuration.scmConfig)
        val projectConfig = BitbucketCloudProjectConfigurationProperty(
            configuration = bitbucketCloudConfig,
            workspace = workspace,
            repository = repository,
            indexationInterval = configuration.scmIndexationInterval ?: 0,
            issueServiceConfigurationIdentifier = configuration.getActualIssueServiceIdentifier(env)
                ?.toRepresentation(),
        )
        val existingConfig =
            propertyService.getPropertyValue(project, BitbucketCloudProjectConfigurationPropertyType::class.java)
        if (existingConfig == null || !sameProperty(existingConfig, projectConfig)) {
            propertyService.editProperty(
                project,
                BitbucketCloudProjectConfigurationPropertyType::class.java,
                projectConfig
            )
        }
    }

    /**
     * The property is not a data class, so compares it field by field.
     */
    private fun sameProperty(
        a: BitbucketCloudProjectConfigurationProperty,
        b: BitbucketCloudProjectConfigurationProperty,
    ) = a.configuration.name == b.configuration.name &&
            a.workspace == b.workspace &&
            a.repository == b.repository &&
            a.indexationInterval == b.indexationInterval &&
            a.issueServiceConfigurationIdentifier == b.issueServiceConfigurationIdentifier

    internal fun getWorkspaceAndRepository(scmUrl: String): Pair<String, String> {
        val m = scmUrlRegex.matchEntire(scmUrl)
            ?: throw BitbucketCloudSCMRepositoryNotDetectedException(scmUrl)
        return m.groupValues[1] to m.groupValues[2]
    }

    internal fun getBitbucketCloudConfiguration(scmConfig: String?): BitbucketCloudConfiguration =
        if (!scmConfig.isNullOrBlank()) {
            bitbucketCloudConfigurationService.getConfiguration(scmConfig)
        } else {
            val configurations = bitbucketCloudConfigurationService.configurations
            when (configurations.size) {
                0 -> throw BitbucketCloudSCMNoConfigException()
                1 -> configurations.first()
                else -> throw BitbucketCloudSCMAmbiguousConfigException(configurations.map { it.name })
            }
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

    companion object {
        /**
         * HTTPS (with or without a user) and SSH forms of a `bitbucket.org` repository URL,
         * capturing the workspace and the repository slugs.
         */
        private val scmUrlRegex =
            "^(?:https?://(?:[^@/]+@)?bitbucket\\.org/|ssh://git@bitbucket\\.org/|git@bitbucket\\.org:)([^/]+)/([^/]+?)(?:\\.git)?/?$".toRegex()
    }
}
