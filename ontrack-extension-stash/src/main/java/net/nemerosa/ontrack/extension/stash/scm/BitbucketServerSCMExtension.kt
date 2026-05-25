package net.nemerosa.ontrack.extension.stash.scm

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.nemerosa.ontrack.extension.git.casc.GitConfigService
import net.nemerosa.ontrack.extension.git.model.getCommitLink
import net.nemerosa.ontrack.extension.git.model.gitRepository
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationProperty
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
import net.nemerosa.ontrack.extension.stash.StashExtensionFeature
import net.nemerosa.ontrack.extension.stash.client.BitbucketClient
import net.nemerosa.ontrack.extension.stash.client.BitbucketClientFactory
import net.nemerosa.ontrack.extension.stash.model.BitbucketRepository
import net.nemerosa.ontrack.extension.stash.model.StashConfiguration
import net.nemerosa.ontrack.extension.stash.model.getRepositoryUrl
import net.nemerosa.ontrack.extension.stash.property.StashConfigurator
import net.nemerosa.ontrack.extension.stash.property.StashGitConfiguration
import net.nemerosa.ontrack.extension.stash.property.StashProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.stash.service.StashConfigurationService
import net.nemerosa.ontrack.extension.stash.settings.BitbucketServerSettings
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.git.GitRepositoryClientFactory
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class BitbucketServerSCMExtension(
    extensionFeature: StashExtensionFeature,
    private val propertyService: PropertyService,
    private val structureService: StructureService,
    private val bitbucketClientFactory: BitbucketClientFactory,
    private val cachedSettingsService: CachedSettingsService,
    private val stashConfigurationService: StashConfigurationService,
    private val issueServiceRegistry: IssueServiceRegistry,
    private val ontrackConfigProperties: OntrackConfigProperties,
    private val bitbucketServerConfigurator: StashConfigurator,
    private val gitRepositoryClientFactory: GitRepositoryClientFactory,
    private val gitConfigService: GitConfigService,
) : AbstractExtension(extensionFeature), SCMExtension {

    private val logger: Logger = LoggerFactory.getLogger(BitbucketServerSCMExtension::class.java)

    override val type: String = "bitbucket-server"

    override fun getSCMPath(configName: String, ref: String): SCMPath? {
        val config = stashConfigurationService.findConfiguration(configName)
            ?: return null
        val regex = "([^\\/]*)\\/([^\\/]*)\\/(.*)\$".toRegex()
        val m = regex.matchEntire(ref)
            ?: throw BitbucketServerSCMRefParsingException("Cannot get the project and repository out of the reference: $ref")
        val (_, project, repository, path) = m.groupValues
        val scm = BitbucketServerSCM(
            configuration = config,
            project = project,
            repositoryName = repository,
            issueServiceConfigurationIdentifier = null,
            settings = cachedSettingsService.getCachedSettings(BitbucketServerSettings::class.java)
        )
        return SCMPath(
            scm = scm,
            path = path,
        )
    }

    override fun getSCM(project: Project): SCM? {
        val property =
            propertyService.getPropertyValue(project, StashProjectConfigurationPropertyType::class.java) ?: return null
        return BitbucketServerSCM(
            configuration = property.configuration,
            project = property.project,
            repositoryName = property.repository,
            issueServiceConfigurationIdentifier = property.issueServiceConfigurationIdentifier,
            settings = cachedSettingsService.getCachedSettings(BitbucketServerSettings::class.java),
        )
    }

    private inner class BitbucketServerSCM(
        private val configuration: StashConfiguration,
        private val project: String,
        private val repositoryName: String,
        private val issueServiceConfigurationIdentifier: String?,
        private val settings: BitbucketServerSettings,
    ) : SCMChangeLogEnabled {

        override val type: String = "git"
        override val engine: String = "bitbucket-server"

        private val stashConfiguration = StashGitConfiguration(
            configuration = configuration,
            project = project,
            repository = repositoryName,
            indexationInterval = 0, // Not needed in this context
            configuredIssueService = null, // Not needed in this context
        )

        private val repo = BitbucketRepository(project, repositoryName)

        override val repositoryURI: String = stashConfiguration.remote

        override val repositoryHtmlURL: String = getRepositoryUrl(configuration, project, repositoryName)

        override val repository: String = "${project}/${repositoryName}"

        override fun getSCMBranch(branch: Branch): String? {
            val branchProperty: GitBranchConfigurationProperty? =
                propertyService.getProperty(branch, GitBranchConfigurationPropertyType::class.java).value
            return branchProperty?.branch
        }

        override fun getBranchLastCommit(branch: String): String? =
            client.geBranchLastCommit(repo, branch)

        override fun deleteBranch(branch: String) {
            client.deleteBranch(repo, branch)
        }

        override fun createBranch(sourceBranch: String, newBranch: String): String =
            client.createBranch(repo, sourceBranch, newBranch)

        override fun download(scmBranch: String?, path: String, retryOnNotFound: Boolean): ByteArray? =
            client.download(repo, scmBranch, path)

        override fun upload(scmBranch: String, commit: String, path: String, content: ByteArray, message: String) {
            client.upload(
                repo = repo,
                branch = scmBranch,
                commit = commit,
                path = path,
                content = content,
                message = message
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
        ): SCMPullRequest {
            // Creates the pull request
            val pr = client.createPR(
                repo = repo,
                title = title,
                head = from,
                base = to,
                body = description,
                reviewers = reviewers,
            )
            val prId = pr.id
            // Auto approval process (approval + wait for checks + merge)
            var status = SCMPullRequestStatus.UNKNOWN
            // Auto approval
            if (autoApproval) {
                // Auto merge token must be set
                if (!configuration.autoMergeToken.isNullOrBlank()) {
                    if (configuration.autoMergeUser.isNullOrBlank()) {
                        throw BitbucketServerSCMMissingAutoMergeUserException(configuration.name)
                    }
                    // Approving using the auto merge account
                    client.approvePR(
                        repo = repo,
                        prId = pr.id,
                        user = configuration.autoMergeUser,
                        token = configuration.autoMergeToken,
                    )
                }
                // Auto merge
                if (remoteAutoMerge) {
                    error("Server-side auto merge is not supported for Bitbucket Server")
                } else {
                    status = waitAndMerge(prId, from, message)
                }
            }
            // PR
            return toSCMPullRequest(pr, status=status)
        }

        private fun toSCMPullRequest(
            pr: BitbucketServerPR,
            status: SCMPullRequestStatus = pr.status,
        ): SCMPullRequest = SCMPullRequest(
            id = pr.id.toString(),
            name = "PR-${pr.id}",
            link = "${configuration.url}/projects/${project}/repos/${repositoryName}/pull-requests/${pr.id}/overview",
            status = status,
        )

        override fun getPullRequestByName(prName: String): SCMPullRequest? =
            if (prName.startsWith("PR-")) {
                val id = prName.substringAfter("PR-").toInt()
                client.getPR(repo, id)?.let { toSCMPullRequest(it) }
            } else {
                null
            }

        override fun getDiffLink(commitFrom: String, commitTo: String): String {
            return "${configuration.url}/projects/${project}/repos/${repositoryName}/compare/commits?targetBranch=${commitTo}&sourceBranch=${commitFrom}"
        }

        override fun getBuildCommit(build: Build): String? =
            propertyService.getPropertyValue(build, GitCommitPropertyType::class.java)?.commit

        override suspend fun getCommits(fromCommit: String, toCommit: String): List<SCMCommit> {
            val commits = client.getCommits(repo, fromCommit, toCommit)
                .takeIf { it.isNotEmpty() }
                ?: client.getCommits(repo, toCommit, fromCommit)
            return commits
                .map { commit ->
                    BitbucketServerSCMCommit(
                        root = configuration.url,
                        repo = repo,
                        commit = commit
                    )
                }
        }

        override fun getCommit(id: String): SCMCommit? =
            if (ontrackConfigProperties.configurationTest) {
                client.getCommit(repo, id)?.let {
                    BitbucketServerSCMCommit(
                        root = configuration.url,
                        repo = repo,
                        commit = it
                    )
                }
            } else {
                null
            }

        override fun getConfiguredIssueService(): ConfiguredIssueService? =
            issueServiceConfigurationIdentifier?.let { issueServiceRegistry.getConfiguredIssueService(it) }

        override val issueRepositoryContext = IssueRepositoryContext(
            repositoryType = "stash",
            repositoryName = repositoryName,
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

        override fun findBranchFromScmBranchName(
            project: Project,
            scmBranch: String
        ): Branch? =
            GitBranchConfigurationPropertyTypeUtils.findBranchFromScmBranchName(
                propertyService = propertyService,
                structureService = structureService,
                project = project,
                scmBranch = scmBranch,
            )

        override fun getBranchesForCommit(project: Project, commit: String): List<String> =
            client.getBranchesForCommit(repo, commit)

        override fun forAllCommits(
            project: Project,
            filter: SCMCommitFilter,
            code: (commit: SCMCommit) -> Unit
        ) {
            val configuration = bitbucketServerConfigurator.getConfiguration(project)
            val gitRepo = configuration?.gitRepository
                ?: error("Cannot get the Git repository for project $project")
            val gitRepoClient = gitRepositoryClientFactory.getClient(gitRepo, gitConfigService.gitConnectionConfig)
            gitRepoClient.sync { logger.info(it) }
            gitRepoClient.forCommits(
                sinceCommit = filter.sinceCommit,
                sinceCommitTimestamp = filter.sinceCommitTimestamp,
                count = filter.count,
            ) { gitCommit ->
                val scmCommit = SimpleSCMCommit(
                    id = gitCommit.id,
                    shortId = gitCommit.shortId,
                    author = gitCommit.author.name,
                    authorEmail = gitCommit.author.email,
                    timestamp = gitCommit.commitTime,
                    message = gitCommit.fullMessage,
                    link = configuration.getCommitLink(gitCommit.id),
                )
                code(scmCommit)
            }
        }

        private fun waitAndMerge(prId: Int, from: String, message: String): SCMPullRequestStatus {
            // Waits for the PR checks to be OK
            val autoApprovalTimeoutMillis = settings.autoMergeTimeout
            val autoApprovalIntervalMillis = settings.autoMergeInterval
            val merged = runBlocking {
                withTimeoutOrNull(timeMillis = autoApprovalTimeoutMillis) {
                    while (!client.isPRMergeable(repo, prId)) {
                        delay(autoApprovalIntervalMillis)
                    }
                    true // PR has become mergeable
                }
            }
            if (merged == null || !merged) {
                return client.getPR(repo, prId)?.status ?: SCMPullRequestStatus.UNKNOWN
            }
            // Merges the PR
            client.mergePR(
                repo,
                prId,
                message
            )
            // Deleting the source branch
            if (settings.autoDeleteBranch) {
                client.deleteBranch(repo, from)
            }
            // Merged
            return SCMPullRequestStatus.MERGED
        }

        override fun mergeBranch(head: String, base: String): SCMCommit {
            val gitRepoClient = gitRepositoryClientFactory.getClient(
                stashConfiguration.gitRepository,
                gitConfigService.gitConnectionConfig,
            )
            val gitCommit = gitRepoClient.mergeBranch(head, base)
            return SimpleSCMCommit(
                id = gitCommit.id,
                shortId = gitCommit.shortId,
                author = gitCommit.author.name,
                authorEmail = gitCommit.author.email,
                timestamp = gitCommit.commitTime,
                message = gitCommit.fullMessage,
                link = stashConfiguration.getCommitLink(gitCommit.id),
            )
        }

        private val client: BitbucketClient by lazy {
            bitbucketClientFactory.getBitbucketClient(configuration)
        }

    }
}