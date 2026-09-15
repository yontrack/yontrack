package net.nemerosa.ontrack.extension.bitbucket.cloud.scm

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.nemerosa.ontrack.extension.bitbucket.cloud.BitbucketCloudExtensionFeature
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClient
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.model.BitbucketCloudMergeOutcome
import net.nemerosa.ontrack.extension.bitbucket.cloud.model.BitbucketCloudPullRequest
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
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
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
    private val ontrackConfigProperties: OntrackConfigProperties,
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
        ): SCMPullRequest {
            // Checks before creating anything, so that a rejected request leaves no pull request behind
            val approverClient = if (autoApproval) {
                if (remoteAutoMerge) {
                    throw BitbucketCloudSCMRemoteAutoMergeNotSupportedException()
                }
                getApproverClient()
            } else {
                null
            }
            // Creating the pull request
            val pr = client.createPullRequest(
                workspace = workspace,
                repository = repositorySlug,
                from = from,
                to = to,
                title = title,
                description = description,
                reviewers = if (reviewers.isEmpty()) emptyList() else client.resolveReviewers(workspace, reviewers),
            )
            // Approval + waiting for the pull request to be mergeable + merge
            val status = if (approverClient != null) {
                approverClient.approvePullRequest(workspace, repositorySlug, pr.id)
                waitAndMerge(pr.id, message)
            } else {
                toStatus(pr.state)
            }
            return toSCMPullRequest(pr, status)
        }

        /**
         * Bitbucket Cloud forbids approving one's own pull request: the approval is done by the auto-merge identity.
         */
        private fun getApproverClient(): BitbucketCloudClient {
            val email = configuration.autoMergeEmail?.takeIf { it.isNotBlank() }
            val token = configuration.autoMergeToken?.takeIf { it.isNotBlank() }
            if (email == null || token == null) {
                throw BitbucketCloudSCMMissingAutoMergeIdentityException(configuration.name)
            }
            return clientFactory.getBitbucketCloudClient(
                configuration.copy(
                    authType = BitbucketCloudAuthType.API_TOKEN,
                    email = email,
                    token = token,
                )
            )
        }

        /**
         * Polls the pull request until it is merged, declined or the timeout is reached. At each attempt, when all
         * the builds of the pull request have passed, a merge is tried: Bitbucket Cloud refuses it while its merge
         * checks do not pass.
         */
        private fun waitAndMerge(prId: Int, message: String): SCMPullRequestStatus {
            val settings = cachedSettingsService.getCachedSettings(BitbucketCloudSettings::class.java)
            val status = runBlocking {
                withTimeoutOrNull(settings.autoMergeTimeout) {
                    var result: SCMPullRequestStatus? = null
                    while (result == null) {
                        result = tryMerge(prId, message, settings)
                        if (result == null) {
                            delay(settings.autoMergeInterval)
                        }
                    }
                    result
                }
            }
            return status ?: run {
                logger.info("Pull request $prId in ${property.fullName} could not be merged in time")
                client.getPullRequest(workspace, repositorySlug, prId)?.let { toStatus(it.state) }
                    ?: SCMPullRequestStatus.UNKNOWN
            }
        }

        /**
         * One attempt to merge, returning `null` when to try again.
         */
        private fun tryMerge(prId: Int, message: String, settings: BitbucketCloudSettings): SCMPullRequestStatus? {
            val state = client.getPullRequest(workspace, repositorySlug, prId)?.state
            when (toStatus(state)) {
                SCMPullRequestStatus.MERGED -> return SCMPullRequestStatus.MERGED
                SCMPullRequestStatus.DECLINED -> return SCMPullRequestStatus.DECLINED
                else -> {}
            }
            val statuses = client.getPullRequestStatuses(workspace, repositorySlug, prId)
            if (statuses.any { it != "SUCCESSFUL" }) {
                return null
            }
            val outcome = client.mergePullRequest(
                workspace = workspace,
                repository = repositorySlug,
                id = prId,
                strategy = settings.mergeStrategy.name,
                message = message,
                closeSourceBranch = settings.autoDeleteBranch,
            )
            return if (outcome == BitbucketCloudMergeOutcome.MERGED) SCMPullRequestStatus.MERGED else null
        }

        private fun toStatus(state: String?): SCMPullRequestStatus =
            when (state) {
                "OPEN" -> SCMPullRequestStatus.OPEN
                "MERGED" -> SCMPullRequestStatus.MERGED
                "DECLINED", "SUPERSEDED" -> SCMPullRequestStatus.DECLINED
                else -> SCMPullRequestStatus.UNKNOWN
            }

        private fun toSCMPullRequest(pr: BitbucketCloudPullRequest, status: SCMPullRequestStatus) = SCMPullRequest(
            id = pr.id.toString(),
            name = "PR-${pr.id}",
            link = pr.links?.html?.href ?: "$repositoryHtmlURL/pull-requests/${pr.id}",
            status = status,
        )

        override fun getPullRequestByName(prName: String): SCMPullRequest? =
            prName.takeIf { it.startsWith("PR-") }
                ?.substringAfter("PR-")
                ?.toIntOrNull()
                ?.let { client.getPullRequest(workspace, repositorySlug, it) }
                ?.let { toSCMPullRequest(it, toStatus(it.state)) }

        /**
         * Through the local clone, as for Bitbucket Server.
         */
        override fun mergeBranch(head: String, base: String): SCMCommit {
            val gitCommit = gitRepositoryClientFactory
                .getClient(gitConfiguration.gitRepository, gitConfigService.gitConnectionConfig)
                .mergeBranch(head, base)
            return SimpleSCMCommit(
                id = gitCommit.id,
                shortId = gitCommit.shortId,
                author = gitCommit.author.name,
                authorEmail = gitCommit.author.email,
                timestamp = gitCommit.commitTime,
                message = gitCommit.fullMessage,
                link = gitConfiguration.getCommitLink(gitCommit.id),
            )
        }

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

        /**
         * Commit from the REST API. Like for Bitbucket Server, not called when the configurations are not tested,
         * since setting the commit property of a build indexes its commit on the spot.
         */
        override fun getCommit(id: String): SCMCommit? =
            if (ontrackConfigProperties.configurationTest) {
                client.getCommit(workspace, repositorySlug, id)?.let {
                    BitbucketCloudSCMCommit(it, repositoryHtmlURL)
                }
            } else {
                null
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

class BitbucketCloudSCMRemoteAutoMergeNotSupportedException : InputException(
    "Server-side auto merge is not supported for Bitbucket Cloud: its API cannot schedule a merge when the checks pass. Disable the remote auto merge and let Yontrack merge the pull request."
)

class BitbucketCloudSCMMissingAutoMergeIdentityException(configuration: String) : InputException(
    "The Bitbucket Cloud configuration $configuration has no auto merge identity (auto merge email and token). It is required to approve the pull requests, since Bitbucket Cloud forbids approving one's own pull request."
)
