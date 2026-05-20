package net.nemerosa.ontrack.extension.github.scm

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.extension.git.casc.GitConfigService
import net.nemerosa.ontrack.extension.git.model.getCommitLink
import net.nemerosa.ontrack.extension.git.model.gitRepository
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationProperty
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyTypeUtils
import net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType
import net.nemerosa.ontrack.extension.github.GitHubExtensionFeature
import net.nemerosa.ontrack.extension.github.GitHubIssueServiceExtension
import net.nemerosa.ontrack.extension.github.catalog.GitHubSCMCatalogSettings
import net.nemerosa.ontrack.extension.github.client.GitHubPR
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClient
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClientFactory
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.property.GitHubConfigurator
import net.nemerosa.ontrack.extension.github.property.GitHubProjectConfigurationProperty
import net.nemerosa.ontrack.extension.github.property.GitHubProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService
import net.nemerosa.ontrack.extension.github.service.GitHubIssueServiceConfiguration
import net.nemerosa.ontrack.extension.issues.IssueRepositoryContext
import net.nemerosa.ontrack.extension.issues.IssueServiceRegistry
import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfigurationRepresentation
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
 * Implementation of the [SCM] interface for GitHub.
 */
@Component
class GitHubSCMExtension(
    gitHubExtensionFeature: GitHubExtensionFeature,
    private val propertyService: PropertyService,
    private val clientFactory: OntrackGitHubClientFactory,
    private val cachedSettingsService: CachedSettingsService,
    private val gitHubConfigurationService: GitHubConfigurationService,
    private val issueServiceRegistry: IssueServiceRegistry,
    private val issueServiceExtension: GitHubIssueServiceExtension,
    private val structureService: StructureService,
    private val gitRepositoryClientFactory: GitRepositoryClientFactory,
    private val gitHubConfigurator: GitHubConfigurator,
    private val gitConfigService: GitConfigService,
) : AbstractExtension(gitHubExtensionFeature), SCMExtension {

    private val logger: Logger = LoggerFactory.getLogger(GitHubSCMExtension::class.java)

    override fun getSCM(project: Project): SCM? {
        val property: GitHubProjectConfigurationProperty? =
            propertyService.getProperty(project, GitHubProjectConfigurationPropertyType::class.java).value
        val settings = cachedSettingsService.getCachedSettings(GitHubSCMCatalogSettings::class.java)
        return property?.run {
            GitHubSCM(
                configuration = property.configuration,
                repository = property.repository,
                issueServiceConfigurationIdentifier = property.issueServiceConfigurationIdentifier,
                settings = settings,
            )
        }
    }

    override val type: String = "github"

    override fun getSCMPath(configName: String, ref: String): SCMPath? {
        val config = gitHubConfigurationService.findConfiguration(configName)
            ?: return null
        val regex = "([^\\/]*)\\/([^\\/]*)\\/(.*)\$".toRegex()
        val m = regex.matchEntire(ref)
            ?: throw GitHubRefParsingException("Cannot get the repository out of the reference: $ref")
        val (_, owner, repo, path) = m.groupValues
        val scm = GitHubSCM(
            configuration = config,
            repository = "$owner/$repo",
            issueServiceConfigurationIdentifier = null,
            settings = cachedSettingsService.getCachedSettings(GitHubSCMCatalogSettings::class.java),
        )
        return SCMPath(
            scm = scm,
            path = path,
        )
    }

    private inner class GitHubSCM(
        private val configuration: GitHubEngineConfiguration,
        override val repository: String,
        private val issueServiceConfigurationIdentifier: String?,
        private val settings: GitHubSCMCatalogSettings,
    ) : SCMChangeLogEnabled {

        override val type: String = "git"
        override val engine: String = "github"

        override val repositoryURI: String = "${configuration.url}/${repository}.git"

        override val repositoryHtmlURL: String = "${configuration.url}/${repository}"

        override fun getDiffLink(commitFrom: String, commitTo: String): String {
            return "${repositoryHtmlURL}/compare/${commitFrom}...${commitTo}"
        }

        override fun getSCMBranch(branch: Branch): String? {
            val branchProperty: GitBranchConfigurationProperty? =
                propertyService.getProperty(branch, GitBranchConfigurationPropertyType::class.java).value
            return branchProperty?.branch
        }

        override fun getBranchLastCommit(branch: String): String? {
            return client.getBranchLastCommit(repository, branch, retryOnNotFound = false)
        }

        override fun forAllCommits(
            project: Project,
            filter: SCMCommitFilter,
            code: (commit: SCMCommit) -> Unit
        ) {
            val configuration = gitHubConfigurator.getConfiguration(project)
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

        override fun deleteBranch(branch: String) {
            if (client.getBranchLastCommit(repository, branch, retryOnNotFound = false) != null) {
                client.deleteBranch(repository, branch)
            }
        }

        override fun createBranch(sourceBranch: String, newBranch: String): String {
            return client.createBranch(repository, sourceBranch, newBranch)
                ?: throw GitHubCannotCreateBranchException(
                    """Cannot create branch $newBranch from $sourceBranch."""
                )
        }

        override fun download(scmBranch: String?, path: String, retryOnNotFound: Boolean): ByteArray? =
            client.getFileContent(repository, scmBranch, path, retryOnNotFound)

        override fun upload(scmBranch: String, commit: String, path: String, content: ByteArray, message: String) {
            // First, we need the SHA of the file
            val sha = client.getFile(repository, scmBranch, path, retryOnNotFound = true)
                ?.sha
                ?: error("Cannot find file at $path for branch $scmBranch")
            client.setFileContent(repository, scmBranch, sha, path, content, message)
        }

        /**
         * See https://developer.github.com/v3/pulls/#create-a-pull-request
         */
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
                repository = repository,
                title = title,
                head = from,
                base = to,
                body = description,
                reviewers = reviewers,
            )
            val prId = pr.number
            // Auto approval process (approval + wait for checks + merge)
            var status = SCMPullRequestStatus.UNKNOWN
            // Auto approval
            if (autoApproval) {
                // Approving using the auto merge account if provided
                if (!configuration.autoMergeToken.isNullOrBlank()) {
                    client.approvePR(
                        repository = repository,
                        pr = pr.number,
                        body = "Automated review for auto versioning on promotion",
                        token = configuration.autoMergeToken,
                    )
                }
                // Auto merge
                if (remoteAutoMerge) {
                    client.enableAutoMerge(repository, pr.number, message)
                } else {
                    status = waitAndMerge(prId, message)
                }
            }
            // PR
            return toSCMPullRequest(pr, status)
        }

        private fun toSCMPullRequest(
            pr: GitHubPR,
            status: SCMPullRequestStatus = pr.status,
        ): SCMPullRequest = SCMPullRequest(
            id = pr.number.toString(),
            name = "#${pr.number}",
            link = pr.html_url ?: "",
            status = status
        )

        override fun getPullRequestByName(prName: String): SCMPullRequest? =
            if (prName.startsWith("#")) {
                val id = prName.substringAfter("#").toInt()
                client.getPR(repository, id)?.let { toSCMPullRequest(it) }
            } else {
                null
            }

        private fun waitAndMerge(prId: Int, message: String): SCMPullRequestStatus {
            // Waits for the PR checks to be OK
            // See https://docs.github.com/en/free-pro-team@latest/rest/guides/getting-started-with-the-git-database-api#checking-mergeability-of-pull-requests
            // for a reference
            val autoApprovalTimeoutMillis = settings.autoMergeTimeout
            val autoApprovalIntervalMillis = settings.autoMergeInterval
            val merged = runBlocking {
                withTimeoutOrNull(timeMillis = autoApprovalTimeoutMillis) {
                    while (!client.isPRMergeable(repository, prId)) {
                        delay(autoApprovalIntervalMillis)
                    }
                    true // PR has become mergeable
                }
            }
            if (merged == null || !merged) {
                return client.getPR(repository, prId)?.status ?: SCMPullRequestStatus.UNKNOWN
            }
            // Merges the PR
            client.mergePR(
                repository,
                prId,
                message
            )
            // Merged
            return SCMPullRequestStatus.MERGED
        }

        override fun getBuildCommit(build: Build): String? =
            propertyService.getPropertyValue(build, GitCommitPropertyType::class.java)?.commit

        override fun getConfiguredIssueService(): ConfiguredIssueService? {
            return if (
                issueServiceConfigurationIdentifier.isNullOrBlank() ||
                IssueServiceConfigurationRepresentation.isSelf(issueServiceConfigurationIdentifier)
            ) {
                ConfiguredIssueService(
                    issueServiceExtension,
                    GitHubIssueServiceConfiguration(
                        configuration,
                        repository
                    )
                )
            } else {
                issueServiceRegistry.getConfiguredIssueService(issueServiceConfigurationIdentifier)
            }
        }

        override val issueRepositoryContext = IssueRepositoryContext(
            repositoryType = "github",
            repositoryName = repository,
        )

        override suspend fun getCommits(fromCommit: String, toCommit: String): List<SCMCommit> {
            val commits = client.compareCommits(repository, fromCommit, toCommit)
                .takeIf { it.isNotEmpty() }
                ?: client.compareCommits(repository, toCommit, fromCommit)
            return commits.map { commit ->
                GitHubSCMCommit(commit)
            }
        }

        override fun getCommit(
            id: String
        ): SCMCommit? =
            client.getCommit(
                repository = repository,
                commit = id,
            )?.let {
                GitHubSCMCommit(it)
            }

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

        /**
         * As of 2025, Sep 26, there is no API endpoint in GitHub (REST or GraphQL) to
         * get the list of branches for a commit.
         *
         * We're now using the local Git repository client (file & `git` based) to
         * get this information.
         *
         * In V6, this will need to be converted into something else.
         */
        override fun getBranchesForCommit(project: Project, commit: String): List<String> {
            val configuration = gitHubConfigurator.getConfiguration(project) ?: return emptyList()
            val gitRepoClient = gitRepositoryClientFactory.getClient(configuration.gitRepository, gitConfigService.gitConnectionConfig)
            return gitRepoClient.getBranchesForCommit(commit)
        }

        private val client: OntrackGitHubClient by lazy {
            clientFactory.create(configuration)
        }

    }

    private class GitHubCannotCreateBranchException(message: String) : BaseException(message)

    private class GitHubRefParsingException(message: String) : InputException(message)

}