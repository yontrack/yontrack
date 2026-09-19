package net.nemerosa.ontrack.extension.gitlab.scm

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
import net.nemerosa.ontrack.extension.gitlab.model.GitLabMergeRequest
import net.nemerosa.ontrack.extension.gitlab.model.GitLabMergeability
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
            return client.download(repository, ref, path, retryOnNotFound)
        }

        /**
         * The `commit` argument is not used, and cannot be: it is the head of the upgrade branch, while
         * GitLab's optimistic concurrency on a file is `last_commit_id`, the last commit **of that file**.
         * The client reads it itself, which is where the protection lives - see
         * [net.nemerosa.ontrack.extension.gitlab.client.GitLabClient.upload].
         */
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
         * Creates a merge request, and - when the auto-versioning order asks for it - approves and merges it.
         *
         * GitLab is the second SCM after GitHub to honour **both** values of auto-versioning's
         * `AutoApprovalMode` rather than reject one:
         *
         * * `CLIENT` - Yontrack approves, polls `detailed_merge_status` until GitLab says `mergeable`, and
         *   merges itself;
         * * `SCM` - Yontrack approves and hands the merge back to GitLab with `auto_merge`, which merges as
         *   soon as everything passes.
         *
         * The approval is the same call in both modes, and it is a **Free** endpoint: only approval *rules*
         * are Premium. There is no separate approver identity either - GitLab does not forbid self-approval,
         * it is the project setting `merge_requests_author_approval`, which is a deployment concern.
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
            val settings = settings()
            val mr = client.createMergeRequest(
                project = repository,
                sourceBranch = from,
                targetBranch = to,
                title = title,
                description = description,
                reviewerIds = resolveReviewers(reviewers),
                removeSourceBranch = settings.removeSourceBranch,
                squash = settings.squash,
            )
            if (!autoApproval) {
                return toSCMPullRequest(mr, toStatus(mr.state))
            }
            // Approving, in both modes: an `auto_merge` on a project which requires an approval would
            // otherwise sit there for ever
            client.approveMergeRequest(repository, mr.iid.toInt())
            val status = if (remoteAutoMerge) {
                autoMerge(mr, message, settings)
            } else {
                waitAndMerge(mr, message, settings)
            }
            return toSCMPullRequest(mr, status)
        }

        /**
         * Turns the reviewer names of the auto-versioning configuration into the numeric `reviewer_ids` the
         * merge request API takes. A name GitLab does not know is skipped rather than failing the order: a
         * missing reviewer is not a reason to leave the version behind.
         */
        private fun resolveReviewers(reviewers: List<String>): List<Long> =
            reviewers.mapNotNull { username ->
                val user = client.findUserByUsername(username)
                if (user == null) {
                    logger.warn("[gitlab] Unknown reviewer $username on $repository, ignored.")
                }
                user?.id
            }

        /**
         * Hands the merge back to GitLab with `auto_merge`.
         *
         * The merge request stays open until GitLab merges it, so the status is [SCMPullRequestStatus.OPEN]
         * unless GitLab merged it on the spot - which it does when there is nothing left to wait for.
         *
         * On a project using **merge trains**, GitLab 19.1 and later routes this into the train rather than
         * merging directly. That is documented rather than enforced: the merge request is still merged, just
         * through the train, and Yontrack has nothing useful to do about it.
         */
        private fun autoMerge(
            mr: GitLabMergeRequest,
            message: String,
            settings: GitLabSettings,
        ): SCMPullRequestStatus {
            val merged = merge(mr, message, settings, autoMerge = true)
            return toStatus(merged.state)
        }

        /**
         * Polls the merge request until GitLab reports it as mergeable, then merges it.
         *
         * The poll reads **`detailed_merge_status`**, never `merge_status`, which has been deprecated since
         * 15.6. A status which cannot resolve itself - a conflict, a rebase needed, an approval still
         * missing - ends the wait at once instead of burning the whole timeout.
         */
        private fun waitAndMerge(
            mr: GitLabMergeRequest,
            message: String,
            settings: GitLabSettings,
        ): SCMPullRequestStatus {
            val iid = mr.iid.toInt()
            val ready: GitLabMergeRequest? = runBlocking {
                withTimeoutOrNull(timeMillis = settings.autoMergeTimeout) {
                    var current: GitLabMergeRequest? = mr
                    var mergeable: GitLabMergeRequest? = null
                    while (mergeable == null) {
                        val state = current ?: break
                        when (state.mergeability) {
                            GitLabMergeability.MERGEABLE -> mergeable = state
                            GitLabMergeability.BLOCKED -> {
                                logger.warn(
                                    "[gitlab] Merge request $PR_NAME_PREFIX$iid of $repository cannot be " +
                                            "merged: ${state.detailed_merge_status}"
                                )
                                break
                            }

                            GitLabMergeability.PENDING -> {
                                delay(settings.autoMergeInterval)
                                current = client.getMergeRequest(repository, iid)
                            }
                        }
                    }
                    mergeable
                }
            }
            if (ready == null) {
                // Timed out, blocked, or gone: the caller reads the outcome from the status
                return client.getMergeRequest(repository, iid)?.let { toStatus(it.state) }
                    ?: SCMPullRequestStatus.UNKNOWN
            }
            val merged = merge(ready, message, settings, autoMerge = false)
            return toStatus(merged.state)
        }

        /**
         * The merge call itself, which always sends the `sha` of the merge request: GitLab 19.2 added a
         * project setting making it mandatory, and a `sha` which no longer matches the source branch is
         * answered with a 409 rather than merging something nobody reviewed.
         *
         * What is squashed is read back from **`squash_on_merge`** rather than repeated from the setting.
         * The setting is what was *asked for*, at creation time; `squash_on_merge` is what GitLab will
         * actually do, and a project can force it either way - on with "Require", off with "Do not allow".
         * Echoing the setting here would ask for the opposite of what the project decided.
         */
        private fun merge(
            mr: GitLabMergeRequest,
            message: String,
            settings: GitLabSettings,
            autoMerge: Boolean,
        ): GitLabMergeRequest {
            val sha = mr.sha
                ?: client.getMergeRequest(repository, mr.iid.toInt())?.sha
                ?: throw GitLabSCMNoMergeRequestShaException(repository, mr.iid.toInt())
            return client.mergeMergeRequest(
                project = repository,
                iid = mr.iid.toInt(),
                sha = sha,
                message = message,
                squash = mr.squash_on_merge,
                removeSourceBranch = settings.removeSourceBranch,
                autoMerge = autoMerge,
            )
        }

        /**
         * A GitLab merge request is named by its `iid` inside the project, which Yontrack writes
         * `PR-123` - as it does on Bitbucket Cloud.
         *
         * Not `#123`: on GitLab that is the syntax of an **issue** reference, which the GitLab issue service
         * of this very module parses. A merge request is `!123` there, and a name mixing the two conventions
         * would read as an issue wherever it is shown.
         */
        override fun getPullRequestByName(prName: String): SCMPullRequest? =
            prName.takeIf { it.startsWith(PR_NAME_PREFIX) }
                ?.substringAfter(PR_NAME_PREFIX)
                ?.toIntOrNull()
                ?.let { client.getMergeRequest(repository, it) }
                ?.let { toSCMPullRequest(it, toStatus(it.state)) }

        private fun toSCMPullRequest(mr: GitLabMergeRequest, status: SCMPullRequestStatus) = SCMPullRequest(
            id = mr.iid.toString(),
            name = "$PR_NAME_PREFIX${mr.iid}",
            link = mr.web_url,
            status = status,
        )

        private fun toStatus(state: String?): SCMPullRequestStatus =
            when (state) {
                "opened", "locked" -> SCMPullRequestStatus.OPEN
                "merged" -> SCMPullRequestStatus.MERGED
                "closed" -> SCMPullRequestStatus.DECLINED
                else -> SCMPullRequestStatus.UNKNOWN
            }

        private fun settings(): GitLabSettings =
            cachedSettingsService.getCachedSettings(GitLabSettings::class.java)

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
            val maxCommits = settings().maxCommits
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

        /**
         * How Yontrack names a GitLab merge request, as on Bitbucket Cloud.
         *
         * Not `#`, which on GitLab names an **issue** - the form this module's own issue service parses.
         */
        const val PR_NAME_PREFIX = "PR-"
    }
}

class GitLabSCMRefParsingException(ref: String) : InputException(
    "Cannot get the GitLab project out of the reference: $ref. Expecting <project path>/<file path>, where the project path holds at least a namespace and a project."
)

class GitLabSCMProjectNotFoundException(configuration: String, ref: String) : InputException(
    "No GitLab project of the configuration $configuration matches the reference: $ref."
)

class GitLabSCMNoMergeRequestShaException(project: String, iid: Int) : InputException(
    "The GitLab merge request ${GitLabSCMExtension.PR_NAME_PREFIX}$iid of the project $project carries no " +
            "sha, which is mandatory to merge it."
)
