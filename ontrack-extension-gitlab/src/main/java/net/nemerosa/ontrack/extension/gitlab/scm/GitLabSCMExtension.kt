package net.nemerosa.ontrack.extension.gitlab.scm

import net.nemerosa.ontrack.extension.git.casc.GitConfigService
import net.nemerosa.ontrack.extension.git.model.getCommitLink
import net.nemerosa.ontrack.extension.git.model.gitRepository
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyTypeUtils
import net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType
import net.nemerosa.ontrack.extension.gitlab.GitLabExtensionFeature
import net.nemerosa.ontrack.extension.gitlab.GitLabIssueServiceExtension
import net.nemerosa.ontrack.extension.gitlab.client.GitLabClient
import net.nemerosa.ontrack.extension.gitlab.client.GitLabClientFactory
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.model.GitLabIssueServiceConfiguration
import net.nemerosa.ontrack.extension.gitlab.property.GitLabGitConfiguration
import net.nemerosa.ontrack.extension.gitlab.property.GitLabProjectConfigurationProperty
import net.nemerosa.ontrack.extension.gitlab.property.GitLabProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import net.nemerosa.ontrack.extension.gitlab.settings.GitLabSettings
import net.nemerosa.ontrack.extension.issues.IssueRepositoryContext
import net.nemerosa.ontrack.extension.issues.IssueServiceRegistry
import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfigurationRepresentation
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommitFilter
import net.nemerosa.ontrack.extension.scm.changelog.SimpleSCMCommit
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.scm.service.SCMExtension
import net.nemerosa.ontrack.extension.scm.service.SCMPath
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequest
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequestStatus
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.git.GitRepositoryClientFactory
import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.StructureService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * [SCMExtension] for GitLab, gitlab.com or self-managed.
 *
 * File references are `scm://gitlab/<configuration>/<project path>/<path>`, where the project path is the
 * full path of the GitLab project, subgroups included - see [getSCMPath] for how the two are told apart.
 *
 * Where the data comes from is aligned with GitHub and Bitbucket Cloud:
 *
 * * [GitLabSCM.forAllCommits] and [GitLabSCM.getBranchesForCommit] read the **local synced clone**, which
 *   costs no API call at all - a full history walk through the API would be thousands of them;
 * * everything else goes through the REST API.
 */
@Component
class GitLabSCMExtension(
    extensionFeature: GitLabExtensionFeature,
    private val propertyService: PropertyService,
    private val structureService: StructureService,
    private val clientFactory: GitLabClientFactory,
    private val cachedSettingsService: CachedSettingsService,
    private val configurationService: GitLabConfigurationService,
    private val issueServiceRegistry: IssueServiceRegistry,
    private val issueServiceExtension: GitLabIssueServiceExtension,
    private val gitRepositoryClientFactory: GitRepositoryClientFactory,
    private val gitConfigService: GitConfigService,
) : AbstractExtension(extensionFeature), SCMExtension {

    private val logger: Logger = LoggerFactory.getLogger(GitLabSCMExtension::class.java)

    override val type: String = TYPE

    override fun getSCM(project: Project): SCM? =
        propertyService.getPropertyValue(project, GitLabProjectConfigurationPropertyType::class.java)
            ?.let { GitLabSCM(it) }

    /**
     * Splits `<project path>/<file path>` and builds the SCM for the project it names.
     *
     * A GitLab project path is **arbitrarily deep** - `group/project`, `group/subgroup/project`,
     * `group/a/b/project` - so, unlike GitHub's `owner/repo`, no fixed number of leading segments identifies
     * the project. The split is therefore resolved against GitLab rather than guessed: every candidate
     * prefix of at least two segments is offered to the API, and the one it recognises as a project wins.
     *
     * At most one candidate can ever match, which is what makes this unambiguous rather than a first-wins
     * heuristic: a GitLab project holds no namespace, so if `group/sub/project` is a project then `group/sub`
     * is necessarily a group and not a project. The shortest candidates are tried first only because a deep
     * file path under a shallow project is the common shape, and that ordering asks the fewest questions.
     *
     * @param configName Name of the GitLab configuration
     * @param ref `<project path>/<file path>`
     */
    override fun getSCMPath(configName: String, ref: String): SCMPath? {
        val configuration = configurationService.findConfiguration(configName) ?: return null
        val segments = ref.split("/").filter { it.isNotBlank() }
        if (segments.size < 3) {
            // Two segments at the very least for the project, one for the file
            throw GitLabSCMRefParsingException(ref)
        }
        val client = clientFactory.create(configuration)
        for (projectSegments in 2 until segments.size) {
            val projectPath = segments.take(projectSegments).joinToString("/")
            if (client.getProject(projectPath) != null) {
                return SCMPath(
                    scm = GitLabSCM(property(configuration, projectPath)),
                    path = segments.drop(projectSegments).joinToString("/"),
                )
            }
        }
        throw GitLabSCMProjectNotFoundException(configName, ref)
    }

    private fun property(configuration: GitLabConfiguration, projectPath: String) =
        GitLabProjectConfigurationProperty(
            configuration = configuration,
            issueServiceConfigurationIdentifier = null,
            repository = projectPath,
            indexationInterval = 0,
        )

    private inner class GitLabSCM(
        private val property: GitLabProjectConfigurationProperty,
    ) : SCMChangeLogEnabled {

        private val configuration: GitLabConfiguration = property.configuration

        /**
         * Full path of the GitLab project, subgroups included.
         */
        override val repository: String = property.repository

        private val gitConfiguration = GitLabGitConfiguration(
            property,
            null, // Not needed in this context
        )

        override val type: String = "git"
        override val engine: String = TYPE

        override val repositoryURI: String = gitConfiguration.remote

        override val repositoryHtmlURL: String = "${configuration.url.trimEnd('/')}/$repository"

        /**
         * GitLab's own compare page, whose `...` form is the merge-base comparison the change log uses.
         */
        override fun getDiffLink(commitFrom: String, commitTo: String): String =
            "$repositoryHtmlURL/-/compare/$commitFrom...$commitTo"

        override fun getSCMBranch(branch: Branch): String? =
            propertyService.getPropertyValue(branch, GitBranchConfigurationPropertyType::class.java)?.branch

        override fun getBranchLastCommit(branch: String): String? =
            client.getBranchLastCommit(repository, branch)

        override fun createBranch(sourceBranch: String, newBranch: String): String =
            client.createBranch(repository, sourceBranch, newBranch)

        override fun deleteBranch(branch: String) {
            client.deleteBranch(repository, branch)
        }

        override fun download(scmBranch: String?, path: String, retryOnNotFound: Boolean): ByteArray? {
            val ref = scmBranch?.takeIf { it.isNotBlank() }
                ?: client.getProject(repository)?.default_branch
                ?: return null
            return client.download(repository, ref, path)
        }

        override fun upload(scmBranch: String, commit: String, path: String, content: ByteArray, message: String) {
            client.upload(
                project = repository,
                branch = scmBranch,
                path = path,
                content = content,
                message = message,
            )
        }

        /**
         * Creating a merge request is the business of the auto-versioning support, which arrives with
         * [issue #1830](https://github.com/yontrack/yontrack/issues/1830). Refusing here is deliberate: a
         * half-built merge request - created but never approved, never merged, never cleaned up - would be
         * worse than an error naming what is missing.
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
        ): SCMPullRequest =
            throw GitLabSCMPullRequestNotSupportedException()

        /**
         * A GitLab merge request is named by its `iid` inside the project, which Yontrack writes `#123`.
         */
        override fun getPullRequestByName(prName: String): SCMPullRequest? =
            prName.takeIf { it.startsWith("#") }
                ?.substringAfter("#")
                ?.toIntOrNull()
                ?.let { client.getMergeRequest(repository, it) }
                ?.let { mr ->
                    SCMPullRequest(
                        id = mr.iid.toString(),
                        name = "#${mr.iid}",
                        link = mr.web_url,
                        status = when (mr.state) {
                            "opened", "locked" -> SCMPullRequestStatus.OPEN
                            "merged" -> SCMPullRequestStatus.MERGED
                            "closed" -> SCMPullRequestStatus.DECLINED
                            else -> SCMPullRequestStatus.UNKNOWN
                        },
                    )
                }

        /**
         * Through the local clone, as for Bitbucket Cloud and Bitbucket Server. The clone is synchronised
         * first: the merge only fetches, which fails on a repository never cloned yet.
         */
        override fun mergeBranch(head: String, base: String): SCMCommit {
            val gitRepoClient = gitRepositoryClient()
            gitRepoClient.sync { logger.info(it) }
            val gitCommit = gitRepoClient.mergeBranch(head, base)
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
         * Commits from `repository/compare`, capped by [GitLabSettings.maxCommits]. When the builds are given
         * in the reverse order the first comparison is empty, and the order is swapped - as on GitHub and
         * Bitbucket Cloud.
         */
        override suspend fun getCommits(fromCommit: String, toCommit: String): List<SCMCommit> {
            val maxCommits = cachedSettingsService.getCachedSettings(GitLabSettings::class.java).maxCommits
            val commits = client.getCommits(repository, fromCommit, toCommit, maxCommits)
                .takeIf { it.isNotEmpty() }
                ?: client.getCommits(repository, toCommit, fromCommit, maxCommits)
            return commits.map { GitLabSCMCommit(it, repositoryHtmlURL) }
        }

        override fun getCommit(id: String): SCMCommit? =
            client.getCommit(repository, id)?.let { GitLabSCMCommit(it, repositoryHtmlURL) }

        override fun getConfiguredIssueService(): ConfiguredIssueService? {
            val identifier = property.issueServiceConfigurationIdentifier
            return if (identifier.isNullOrBlank() || IssueServiceConfigurationRepresentation.isSelf(identifier)) {
                ConfiguredIssueService(
                    issueServiceExtension,
                    GitLabIssueServiceConfiguration(configuration, repository),
                )
            } else {
                issueServiceRegistry.getConfiguredIssueService(identifier)
            }
        }

        override val issueRepositoryContext = IssueRepositoryContext(
            repositoryType = TYPE,
            repositoryName = repository,
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
         * GitLab has no API to list the branches containing a commit: the local clone answers it, as on
         * GitHub and Bitbucket Cloud.
         */
        override fun getBranchesForCommit(project: Project, commit: String): List<String> =
            gitRepositoryClient().getBranchesForCommit(commit)

        /**
         * Commits of the local synced clone, to spare the API rate limit - gitlab.com's announced tier-aware
         * limits drop Free to a 100-request burst per minute, which a full history walk would spend at once.
         */
        override fun forAllCommits(project: Project, filter: SCMCommitFilter, code: (commit: SCMCommit) -> Unit) {
            val gitRepoClient = gitRepositoryClient()
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

        private fun gitRepositoryClient() =
            gitRepositoryClientFactory.getClient(
                gitConfiguration.gitRepository,
                gitConfigService.gitConnectionConfig,
            )

        private val client: GitLabClient by lazy {
            clientFactory.create(configuration)
        }
    }

    companion object {
        const val TYPE = "gitlab"
    }
}

class GitLabSCMRefParsingException(ref: String) : InputException(
    "Cannot get the GitLab project out of the reference: $ref. Expecting <project path>/<file path>, where the project path holds at least a namespace and a project."
)

class GitLabSCMProjectNotFoundException(configuration: String, ref: String) : InputException(
    "No GitLab project of the configuration $configuration matches the reference: $ref."
)

class GitLabSCMPullRequestNotSupportedException : InputException(
    "Creating a GitLab merge request is not supported yet. See https://github.com/yontrack/yontrack/issues/1830."
)
