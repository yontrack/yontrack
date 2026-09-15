package net.nemerosa.ontrack.extension.bitbucket.cloud.scm

import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.extension.bitbucket.cloud.BitbucketCloudExtensionFeature
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClient
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BitbucketCloudGitConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BitbucketCloudProjectConfigurationProperty
import net.nemerosa.ontrack.extension.bitbucket.cloud.property.BitbucketCloudProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.bitbucket.cloud.settings.BitbucketCloudSettings
import net.nemerosa.ontrack.extension.git.casc.GitConfigService
import net.nemerosa.ontrack.extension.git.model.getCommitLink
import net.nemerosa.ontrack.extension.git.model.gitRepository
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyTypeUtils
import net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType
import net.nemerosa.ontrack.extension.issues.IssueRepositoryContext
import net.nemerosa.ontrack.extension.issues.IssueServiceRegistry
import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommitFilter
import net.nemerosa.ontrack.extension.scm.changelog.SimpleSCMCommit
import net.nemerosa.ontrack.extension.scm.service.*
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.git.GitRepositoryClientFactory
import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * [SCMExtension] for Bitbucket Cloud.
 *
 * File references are `scm://bitbucket-cloud/<configuration>/<workspace>/<repository>/<path>`.
 */
@Component
class BitbucketCloudSCMExtension(
    extensionFeature: BitbucketCloudExtensionFeature,
    private val propertyService: PropertyService,
    private val structureService: StructureService,
    private val clientFactory: BitbucketCloudClientFactory,
    private val cachedSettingsService: CachedSettingsService,
    private val configurationService: BitbucketCloudConfigurationService,
    private val issueServiceRegistry: IssueServiceRegistry,
    private val gitRepositoryClientFactory: GitRepositoryClientFactory,
    private val gitConfigService: GitConfigService,
) : AbstractExtension(extensionFeature), SCMExtension {

    private val logger: Logger = LoggerFactory.getLogger(BitbucketCloudSCMExtension::class.java)

    override val type: String = TYPE

    override fun getSCM(project: Project): SCM? {
        val property =
            propertyService.getPropertyValue(project, BitbucketCloudProjectConfigurationPropertyType::class.java)
                ?: return null
        return BitbucketCloudSCM(property)
    }

    /**
     * @param configName Name of the Bitbucket Cloud configuration
     * @param ref `<workspace>/<repository>/<path>`
     */
    override fun getSCMPath(configName: String, ref: String): SCMPath? {
        val configuration = configurationService.findConfiguration(configName) ?: return null
        val m = REF_REGEX.matchEntire(ref)
            ?: throw BitbucketCloudSCMRefParsingException(ref)
        val (_, workspace, repository, path) = m.groupValues
        return SCMPath(
            scm = BitbucketCloudSCM(
                BitbucketCloudProjectConfigurationProperty(
                    configuration = configuration,
                    workspace = workspace,
                    repository = repository,
                    indexationInterval = 0,
                    issueServiceConfigurationIdentifier = null,
                )
            ),
            path = path,
        )
    }

    private inner class BitbucketCloudSCM(
        private val property: BitbucketCloudProjectConfigurationProperty,
    ) : SCMChangeLogEnabled {

        private val configuration: BitbucketCloudConfiguration = property.configuration
        private val workspace: String = property.workspace
        private val repositorySlug: String = property.repository

        private val gitConfiguration = BitbucketCloudGitConfiguration(
            property = property,
            configuredIssueService = null, // Not needed in this context
        )

        override val type: String = "git"
        override val engine: String = TYPE

        override val repositoryURI: String = gitConfiguration.remote

        override val repositoryHtmlURL: String = property.repositoryUrl

        override val repository: String = property.fullName

        /**
         * Bitbucket Cloud compares a source with a destination: what is on [commitTo] and not on [commitFrom].
         */
        override fun getDiffLink(commitFrom: String, commitTo: String): String =
            "$repositoryHtmlURL/branches/compare/$commitTo%0D$commitFrom#diff"

        override fun getSCMBranch(branch: Branch): String? =
            propertyService.getPropertyValue(branch, GitBranchConfigurationPropertyType::class.java)?.branch

        override fun getBranchLastCommit(branch: String): String? =
            client.getBranchLastCommit(workspace, repositorySlug, branch)

        override fun createBranch(sourceBranch: String, newBranch: String): String =
            client.createBranch(workspace, repositorySlug, sourceBranch, newBranch)

        override fun deleteBranch(branch: String) {
            client.deleteBranch(workspace, repositorySlug, branch)
        }

        override fun download(scmBranch: String?, path: String, retryOnNotFound: Boolean): ByteArray? {
            val ref = scmBranch?.takeIf { it.isNotBlank() }
                ?: client.getRepository(workspace, repositorySlug).mainbranch?.name
                ?: return null
            return client.download(workspace, repositorySlug, ref, path)
        }

        override fun upload(scmBranch: String, commit: String, path: String, content: ByteArray, message: String) {
            client.upload(
                workspace = workspace,
                repository = repositorySlug,
                branch = scmBranch,
                path = path,
                content = content,
                message = message,
            )
        }

        override fun createPR(
            from: String,
            to: String,
            title: String,
            description: String,
            autoApproval: Boolean,
            remoteAutoMerge: Boolean,
            message: String,
            reviewers: List<String>,
        ): SCMPullRequest = throw BitbucketCloudSCMNotSupportedYetException("Creating pull requests")

        override fun getPullRequestByName(prName: String): SCMPullRequest? =
            throw BitbucketCloudSCMNotSupportedYetException("Getting pull requests by name")

        override fun mergeBranch(head: String, base: String): SCMCommit =
            throw BitbucketCloudSCMNotSupportedYetException("Merging branches")

        override fun getBuildCommit(build: Build): String? =
            propertyService.getPropertyValue(build, GitCommitPropertyType::class.java)?.commit

        /**
         * Commits from the REST API, capped by [BitbucketCloudSettings.maxCommits]. When the builds are given in
         * the reverse order, the first call is empty and the order is swapped.
         */
        override suspend fun getCommits(fromCommit: String, toCommit: String): List<SCMCommit> {
            val maxCommits = cachedSettingsService.getCachedSettings(BitbucketCloudSettings::class.java).maxCommits
            val commits = client.getCommits(workspace, repositorySlug, fromCommit, toCommit, maxCommits)
                .takeIf { it.isNotEmpty() }
                ?: client.getCommits(workspace, repositorySlug, toCommit, fromCommit, maxCommits)
            return commits.map { BitbucketCloudSCMCommit(it, repositoryHtmlURL) }
        }

        override fun getCommit(id: String): SCMCommit? =
            client.getCommit(workspace, repositorySlug, id)?.let {
                BitbucketCloudSCMCommit(it, repositoryHtmlURL)
            }

        override fun getConfiguredIssueService(): ConfiguredIssueService? =
            property.issueServiceConfigurationIdentifier
                ?.takeIf { it.isNotBlank() }
                ?.let { issueServiceRegistry.getConfiguredIssueService(it) }

        override val issueRepositoryContext = IssueRepositoryContext(
            repositoryType = TYPE,
            repositoryName = property.fullName,
        )

        override fun findBuildByCommit(project: Project, id: String): Build? =
            propertyService.findByEntityTypeAndSearchArguments(
                entityType = ProjectEntityType.BUILD,
                propertyType = GitCommitPropertyType::class,
                searchArguments = GitCommitPropertyType.getGitCommitSearchArguments(id)
            ).map { buildId ->
                structureService.getBuild(buildId)
            }.firstOrNull { build ->
                build.project.id == project.id
            }

        override fun findBranchFromScmBranchName(project: Project, scmBranch: String): Branch? =
            GitBranchConfigurationPropertyTypeUtils.findBranchFromScmBranchName(
                propertyService = propertyService,
                structureService = structureService,
                project = project,
                scmBranch = scmBranch,
            )

        /**
         * Bitbucket Cloud has no API to list the branches containing a commit: the local clone is used.
         */
        override fun getBranchesForCommit(project: Project, commit: String): List<String> =
            gitRepositoryClientFactory.getClient(gitConfiguration.gitRepository, gitConfigService.gitConnectionConfig)
                .getBranchesForCommit(commit)

        /**
         * Commits of the local synced clone, as for GitHub, to spare the API rate limit.
         */
        override fun forAllCommits(project: Project, filter: SCMCommitFilter, code: (commit: SCMCommit) -> Unit) {
            val gitRepoClient =
                gitRepositoryClientFactory.getClient(gitConfiguration.gitRepository, gitConfigService.gitConnectionConfig)
            gitRepoClient.sync { logger.info(it) }
            gitRepoClient.forCommits(
                sinceCommit = filter.sinceCommit,
                sinceCommitTimestamp = filter.sinceCommitTimestamp,
                count = filter.count,
            ) { gitCommit ->
                code(
                    SimpleSCMCommit(
                        id = gitCommit.id,
                        shortId = gitCommit.shortId,
                        author = gitCommit.author.name,
                        authorEmail = gitCommit.author.email,
                        timestamp = gitCommit.commitTime,
                        message = gitCommit.fullMessage,
                        link = gitConfiguration.getCommitLink(gitCommit.id),
                    )
                )
            }
        }

        private val client: BitbucketCloudClient by lazy {
            clientFactory.getBitbucketCloudClient(configuration)
        }
    }

    companion object {
        const val TYPE = "bitbucket-cloud"

        private val REF_REGEX = "([^/]+)/([^/]+)/(.+)$".toRegex()
    }
}

class BitbucketCloudSCMRefParsingException(ref: String) :
    InputException("Cannot get the workspace and repository out of the reference: $ref. Expecting <workspace>/<repository>/<path>.")

class BitbucketCloudSCMNotSupportedYetException(operation: String) :
    BaseException("$operation is not supported yet for Bitbucket Cloud.")
