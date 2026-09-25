package net.nemerosa.ontrack.extension.scm.mock

import net.nemerosa.ontrack.extension.issues.IssueRepositoryContext
import net.nemerosa.ontrack.extension.issues.IssueServiceExtension
import net.nemerosa.ontrack.extension.issues.IssueServiceRegistry
import net.nemerosa.ontrack.extension.issues.model.ConfiguredIssueService
import net.nemerosa.ontrack.extension.issues.model.Issue
import net.nemerosa.ontrack.extension.issues.model.IssueServiceConfiguration
import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.extension.scm.changelog.SCMChangeLogEnabled
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommitFilter
import net.nemerosa.ontrack.extension.scm.service.*
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.extension.ExtensionFeature
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.MessageAnnotation.Companion.of
import net.nemerosa.ontrack.model.support.MessageAnnotator
import net.nemerosa.ontrack.model.support.RegexMessageAnnotator
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
@ConditionalOnProperty(
    prefix = "ontrack.config.extension.scm.mock",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class MockSCMExtension(
    extensionFeature: SCMExtensionFeature,
    private val propertyService: PropertyService,
    private val structureService: StructureService,
    private val issueServiceRegistry: IssueServiceRegistry,
    private val mockSCMStore: MockSCMStore,
) : AbstractExtension(extensionFeature), SCMExtension {

    override fun getSCM(project: Project): SCM? =
        propertyService.getPropertyValue(project, MockSCMProjectPropertyType::class.java)?.let {
            MockSCM(it)
        }

    override val type: String = "mock"

    override fun getSCMPath(configName: String, ref: String): SCMPath? {
        TODO("Not yet implemented")
    }

    private val repositories = mutableMapOf<String, MockRepository>()

    /**
     * Whether [repositories] has been filled from [mockSCMStore] yet.
     *
     * Loading happens on the first access rather than at bean creation: with the default
     * [NoMockSCMStore] there is nothing to load, and where there is, the storage is a
     * database the mock SCM has no reason to touch while the context is still starting.
     */
    private var loaded = false

    @Synchronized
    private fun repositories(): MutableMap<String, MockRepository> {
        if (!loaded) {
            mockSCMStore.loadAll().forEach { data ->
                repositories[data.name] = MockRepository.fromData(data, ::saveRepository)
            }
            // Only once the load went through: a store that cannot answer at all - the database
            // being down, say - must fail again on the next call rather than latch the mock SCM
            // into looking permanently empty.
            loaded = true
        }
        return repositories
    }

    private fun saveRepository(repository: MockRepository) {
        mockSCMStore.save(repository.toData())
    }

    /**
     * Registers an empty repository, discarding whatever the previous one of that name held —
     * in the store as well, so that a re-registration does not leave the old commits behind
     * for the next restart to bring back.
     */
    fun registerRepository(name: String) {
        val repository = MockRepository(name, ::saveRepository)
        repositories()[name] = repository
        saveRepository(repository)
    }

    /**
     * Unlike [registerRepository] this does not write the repository it creates: the read
     * endpoints go through here, and an empty repository is what the next start would build
     * anyway. It reaches the store as soon as anything is registered in it.
     */
    fun repositoryOrCreate(name: String): MockRepository =
        repositories().getOrPut(name) { MockRepository(name, ::saveRepository) }

    fun repository(name: String): MockRepository = repositories()[name]
        ?: error("Repository $name not found")

    /**
     * The repository, or `null` when there is none — where [repository] fails and
     * [repositoryOrCreate] would create one.
     */
    fun findRepository(name: String): MockRepository? = repositories()[name]

    fun deleteRepository(repoName: String) {
        repositories().remove(repoName)
        mockSCMStore.delete(repoName)
    }

    data class MockPullRequest(
        val from: String,
        val to: String,
        val id: Int,
        val title: String,
        val body: String,
        val approved: Boolean,
        val status: SCMPullRequestStatus,
        val reviewers: List<String>,
    ) {
        fun toSCMPullRequest(): SCMPullRequest = SCMPullRequest(
            id = id.toString(),
            name = "#$id",
            link = "mock:pr:$id",
            status = status,
        )
    }

    class MockRepository(
        val name: String,
        /**
         * Called after every change to the repository, so that a [MockSCMStore] can keep up.
         * It hands over the whole repository rather than the change, so that no partial write
         * can leave the store disagreeing with this object about where a branch's commits stop
         * — which is what commit ids are derived from. Like the rest of the mock SCM this
         * assumes one writer: nothing here is safe against concurrent registrations.
         */
        private val onChange: (MockRepository) -> Unit = {},
    ) {

        private val revisionCount = AtomicLong(0)

        private val issues = mutableMapOf<String, MockIssue>()

        private val branches = mutableListOf<MockBranch>()

        private val files = mutableMapOf<String, MutableMap<String, String>>()

        /**
         * Branches created using [createBranch], indexed by the name of the created branch,
         * and associated with the branch they were created from.
         */
        private val createdBranches = mutableMapOf<String, String>()
        private val createdPullRequests = mutableListOf<MockPullRequest>()

        fun registerIssue(key: String, message: String, vararg types: String) {
            issues[key] = MockIssue(name, key, message, types = types.toSet())
            save()
        }

        /**
         * Registers a commit.
         *
         * @param scmBranch Branch where to generate the commit
         * @param message Commit message
         */
        fun registerCommit(scmBranch: String, message: String): String {
            val branch = branches.find { it.name == scmBranch } ?: branches.run {
                val newBranch = MockBranch(scmBranch)
                add(newBranch)
                newBranch
            }
            val indexOnBranch = (branch.commits.size + 1)
            val normalizedBranchName = scmBranch.replace(
                "[^a-zA-Z0-9\\.]".toRegex(),
                "-"
            )
            val prefix = "$normalizedBranchName-$indexOnBranch"
            val digest = DigestUtils.sha1Hex(prefix).take(7)
            val id = "$prefix-$digest"
            branch.commits += MockCommit(
                repository = name,
                revision = revisionCount.incrementAndGet(),
                id = id,
                message = message,
            )

            // Collects associated issues and associates the commit to them
            issueRegex.findAll(message).forEach { matchResult ->
                val issueKey = matchResult.groupValues[1]
                val existingIssue = issues[issueKey]
                existingIssue?.addCommitId(id)
            }

            save()

            return id
        }

        fun registerFile(scmBranch: String, path: String, content: String) {
            val branch = files.getOrPut(scmBranch) {
                mutableMapOf()
            }
            branch[path] = content
            save()
        }

        fun getFile(scmBranch: String?, path: String) = files[scmBranch ?: ""]?.get(path)

        fun deleteBranch(branch: String) {
            createdBranches.remove(branch)
            files.remove(branch)
            save()
        }

        fun createBranch(sourceBranch: String, newBranch: String): String {
            createdBranches[newBranch] = sourceBranch
            save()
            return newBranch
        }

        fun createPR(
            from: String,
            to: String,
            title: String,
            body: String,
            autoApproval: Boolean,
            reviewers: List<String>,
        ): SCMPullRequest {
            val id = createdPullRequests.size + 1
            val pr = MockPullRequest(
                from = from,
                to = to,
                id = id,
                title = title,
                body = body,
                approved = autoApproval,
                status = if (autoApproval) SCMPullRequestStatus.MERGED else SCMPullRequestStatus.OPEN, // Merged immediately?
                reviewers = reviewers,
            )

            if (autoApproval) {
                // Merging the PR means that the BASE branch (to) must be adapted
                // Gets the files from the HEAD
                files[from]?.forEach { (path, content) ->
                    registerFile(to, path, content)
                }
            }

            createdPullRequests += pr
            save()
            return pr.toSCMPullRequest()
        }

        fun getPullRequestByName(prName: String): SCMPullRequest? {
            return if (prName.startsWith("#")) {
                val id = prName.substringAfter("#").toInt()
                createdPullRequests.find { it.id == id }?.toSCMPullRequest()
            } else {
                null
            }
        }

        fun findPR(from: String?, to: String?): MockPullRequest? =
            createdPullRequests
                .filter {
                    branchMatches(it.from, from)
                }.firstOrNull {
                    branchMatches(it.to, to)
                }

        private fun branchMatches(
            actualBranch: String,
            branchPattern: String?
        ) = if (branchPattern == null) {
            true
        } else if (branchPattern.endsWith("*")) {
            actualBranch.startsWith(branchPattern.trimEnd('*'))
        } else {
            actualBranch == branchPattern
        }

        /**
         * Looks for a branch, either one which carries some commits, or one which has been
         * created using [createBranch] and not deleted since.
         *
         * @param namePattern Exact branch name or prefix followed by a `*`
         */
        fun getBranch(namePattern: String): MockBranch? {
            val name = (branches.map { it.name } + createdBranches.keys)
                .firstOrNull { branchMatches(it, namePattern) }
                ?: return null
            return branches.find { it.name == name } ?: MockBranch(name)
        }

        fun findIssue(key: String) = issues[key]

        fun getCommits(fromCommit: String, toCommit: String): List<SCMCommit> {
            val fromBranch = branches.find { (_, commits) ->
                commits.any { it.id == fromCommit }
            }
            val toBranch = branches.find { (_, commits) ->
                commits.any { it.id == toCommit }
            }

            if (fromBranch == null || toBranch == null) {
                return emptyList()
            } else if (fromBranch.name == toBranch.name) {
                val allCommits = fromBranch.commits
                val indexFrom = allCommits.indexOfFirst { it.id == fromCommit }
                val indexTo = allCommits.indexOfFirst { it.id == toCommit }
                val lowIndex = minOf(indexFrom, indexTo)
                val highIndex = maxOf(indexFrom, indexTo)
                return if (lowIndex == highIndex) {
                    emptyList()
                } else {
                    allCommits.subList(
                        lowIndex + 1, // We don't want the first commit
                        highIndex + 1, // We want the last commit
                    ).reversed() // We want the commits from the most recent to the oldest
                }
            } else {
                /**
                 * WARNING: THE MOCK SCM IS NOT A SCM AND THE WAY THIS IS COMPUTED BELOW IS ONLY
                 *          SUITABLE FOR TESTS!!
                 */
                // Ordering the branches from the oldest to the newest
                val (oldestBranch, newestBranch) = listOf(fromBranch, toBranch).sortedBy { branch ->
                    branches.indexOfFirst { it.name == branch.name }
                }
                val oldestCommit: String
                val newestCommit: String
                if (oldestBranch.name == fromBranch.name) {
                    oldestCommit = fromCommit
                    newestCommit = toCommit
                } else {
                    oldestCommit = toCommit
                    newestCommit = fromCommit
                }
                // List of commits to gather
                val commits = mutableListOf<MockCommit>()
                // Index branches boundaries
                val oldestBranchIndex = branches.indexOfFirst { it.name == oldestBranch.name }
                val newestBranchIndex = branches.indexOfFirst { it.name == newestBranch.name }
                (oldestBranchIndex..newestBranchIndex).forEach { index ->
                    val branch = branches[index]
                    // If it is the oldest branch, take commits only from the oldest commit (excluded)
                    if (branch.name == oldestBranch.name) {
                        var found = false
                        branch.commits.forEach { commit ->
                            if (found) {
                                commits += commit
                            } else {
                                found = commit.id == oldestCommit
                            }
                        }
                    }
                    // If newest branch, take commits only until the newest commit (included)
                    else if (branch.name == newestBranch.name) {
                        var found = false
                        branch.commits.forEach { commit ->
                            if (!found) {
                                commits += commit
                            }
                            found = commit.id == newestCommit
                        }
                    }
                    // If anything else, take all the commits
                    else {
                        commits += branch.commits
                    }
                }
                // OK
                return commits.reversed()
            }
        }

        fun getCommit(id: String): SCMCommit? =
            branches.flatMap { it.commits }.find { it.id == id }

        fun mergeBranch(head: String, base: String): SCMCommit {
            // Copy all files from the head branch into the base branch
            files[head]?.forEach { (path, content) ->
                registerFile(base, path, content)
            }
            // Register a merge commit on the base branch and return it
            val commitId = registerCommit(base, "Merge $head into $base")
            return getCommit(commitId) ?: MockCommit(
                repository = name,
                revision = 0L,
                id = commitId,
                message = "Merge $head into $base",
            )
        }

        fun getBranchesForCommit(commit: String): List<String> =
            branches.filter { branch ->
                branch.commits.any { it.id == commit }
            }.map { it.name }

        /**
         * All the commits of all the branches, oldest first, as a Git log of all the branches.
         */
        fun forAllCommits(code: (SCMCommit) -> Unit) {
            branches.flatMap { it.commits }.sortedBy { it.revision }.forEach(code)
        }

        private fun save() {
            onChange(this)
        }

        /**
         * Snapshot of everything this repository holds, for a [MockSCMStore].
         */
        fun toData() = MockRepositoryData(
            name = name,
            revision = revisionCount.get(),
            issues = issues.values.map { issue ->
                MockIssueData(
                    key = issue.key,
                    message = issue.message,
                    updateTime = issue.updateTime,
                    types = issue.types,
                    commits = issue.commits.toList(),
                )
            },
            branches = branches.map { branch ->
                MockBranchData(
                    name = branch.name,
                    commits = branch.commits.map { commit ->
                        MockCommitData(
                            id = commit.id,
                            revision = commit.revision,
                            message = commit.message,
                            timestamp = commit.timestamp,
                        )
                    },
                )
            },
            files = files.flatMap { (scmBranch, paths) ->
                paths.map { (path, content) ->
                    MockFileData(scmBranch = scmBranch, path = path, content = content)
                }
            },
            createdBranches = createdBranches.map { (branch, from) ->
                MockCreatedBranchData(branch = branch, from = from)
            },
            pullRequests = createdPullRequests.map { pr ->
                MockPullRequestData(
                    id = pr.id,
                    from = pr.from,
                    to = pr.to,
                    title = pr.title,
                    body = pr.body,
                    approved = pr.approved,
                    status = pr.status,
                    reviewers = pr.reviewers,
                )
            },
        )

        private fun restore(data: MockRepositoryData) {
            revisionCount.set(data.revision)
            data.issues.forEach { issueData ->
                val issue = MockIssue(
                    repositoryName = name,
                    key = issueData.key,
                    message = issueData.message,
                    types = issueData.types,
                    updateTime = issueData.updateTime,
                )
                issueData.commits.forEach(issue::addCommitId)
                issues[issueData.key] = issue
            }
            data.branches.forEach { branchData ->
                branches += MockBranch(
                    name = branchData.name,
                    commits = branchData.commits.map { commitData ->
                        MockCommit(
                            repository = name,
                            revision = commitData.revision,
                            id = commitData.id,
                            message = commitData.message,
                            timestamp = commitData.timestamp,
                        )
                    }.toMutableList(),
                )
            }
            data.files.forEach { fileData ->
                files.getOrPut(fileData.scmBranch) { mutableMapOf() }[fileData.path] = fileData.content
            }
            data.createdBranches.forEach { createdBranchData ->
                createdBranches[createdBranchData.branch] = createdBranchData.from
            }
            data.pullRequests.forEach { prData ->
                createdPullRequests += MockPullRequest(
                    from = prData.from,
                    to = prData.to,
                    id = prData.id,
                    title = prData.title,
                    body = prData.body,
                    approved = prData.approved,
                    status = prData.status,
                    reviewers = prData.reviewers,
                )
            }
        }

        companion object {

            /**
             * Restores a repository from a snapshot, without calling [onChange] back: what is
             * being read is what the store already holds.
             */
            fun fromData(
                data: MockRepositoryData,
                onChange: (MockRepository) -> Unit = {},
            ): MockRepository =
                MockRepository(data.name, onChange).apply { restore(data) }
        }

    }

    private inner class MockSCM(
        private val mockScmProjectProperty: MockSCMProjectProperty,
    ) : SCM, SCMChangeLogEnabled {

        override val type: String = "mock"
        override val engine: String = "mock"

        override val repositoryURI: String = "mock:${mockScmProjectProperty.name}"

        override val repositoryHtmlURL: String = ""

        override val repository: String = mockScmProjectProperty.name

        override fun getSCMBranch(branch: Branch): String? =
            propertyService.getPropertyValue(branch, MockSCMBranchPropertyType::class.java)?.name

        override fun getBranchLastCommit(branch: String): String? =
            repository(mockScmProjectProperty.name).getBranch(branch)?.lastCommit?.id

        override fun deleteBranch(branch: String) {
            repository(mockScmProjectProperty.name).deleteBranch(branch)
        }

        override fun createBranch(sourceBranch: String, newBranch: String): String =
            repository(mockScmProjectProperty.name).createBranch(sourceBranch, newBranch)

        override fun download(scmBranch: String?, path: String, retryOnNotFound: Boolean): ByteArray? =
            repository(mockScmProjectProperty.name).getFile(scmBranch, path)?.toByteArray()

        override fun upload(scmBranch: String, commit: String, path: String, content: ByteArray, message: String) {
            repository(mockScmProjectProperty.name).registerFile(scmBranch, path, content.decodeToString())
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
        ): SCMPullRequest = repository(mockScmProjectProperty.name).createPR(
            from = from,
            to = to,
            title = title,
            body = description,
            autoApproval = autoApproval,
            reviewers = reviewers,
        )

        override fun getPullRequestByName(prName: String): SCMPullRequest? =
            repository(mockScmProjectProperty.name).getPullRequestByName(prName)

        override fun getDiffLink(commitFrom: String, commitTo: String): String =
            "diff?from=$commitFrom&to=$commitTo"

        override fun getBuildCommit(build: Build): String? =
            propertyService.getPropertyValue(build, MockSCMBuildCommitPropertyType::class.java)?.id

        override suspend fun getCommits(fromCommit: String, toCommit: String): List<SCMCommit> =
            repository(mockScmProjectProperty.name).getCommits(fromCommit, toCommit)

        override fun getConfiguredIssueService(): ConfiguredIssueService? =
            if (mockScmProjectProperty.issueServiceIdentifier != null) {
                issueServiceRegistry.getConfiguredIssueService(mockScmProjectProperty.issueServiceIdentifier)
            } else {
                ConfiguredIssueService(
                    MockIssueServiceExtension(repository),
                    MockIssueServiceConfiguration.INSTANCE,
                )
            }

        override val issueRepositoryContext = IssueRepositoryContext(
            repositoryType = "mock",
            repositoryName = mockScmProjectProperty.name,
        )

        override fun findBuildByCommit(project: Project, id: String): Build? =
            propertyService.findByEntityTypeAndSearchArguments(
                entityType = ProjectEntityType.BUILD,
                propertyType = MockSCMBuildCommitPropertyType::class,
                searchArguments = MockSCMBuildCommitProperty.getSearchArguments(id)
            ).map { buildId ->
                structureService.getBuild(buildId)
            }.firstOrNull { build ->
                build.project.id == project.id
            }

        override fun findBranchFromScmBranchName(project: Project, scmBranch: String): Branch? =
            propertyService.findByEntityTypeAndSearchArguments(
                entityType = ProjectEntityType.BRANCH,
                propertyType = MockSCMBranchPropertyType::class,
                searchArguments = MockSCMBranchProperty.getSearchArguments(scmBranch)
            ).map { branchId ->
                structureService.getBranch(branchId)
            }.firstOrNull { branch ->
                branch.project.id == project.id
            }

        override fun getCommit(
            id: String
        ): SCMCommit? =
            repository(mockScmProjectProperty.name).getCommit(id)

        override fun getBranchesForCommit(project: Project, commit: String): List<String> =
            repository(mockScmProjectProperty.name).getBranchesForCommit(commit)

        override fun mergeBranch(
            head: String,
            base: String
        ): SCMCommit = repository(mockScmProjectProperty.name).mergeBranch(head, base)

        override val commitsSinceSupported: Boolean = true

        /**
         * The commits of the repository, oldest first. Like a Git clone, an unknown
         * [SCMCommitFilter.sinceCommit] returns all of them.
         */
        override fun forAllCommits(
            project: Project,
            filter: SCMCommitFilter,
            code: (commit: SCMCommit) -> Unit
        ) {
            val commits = mutableListOf<SCMCommit>()
            repository(mockScmProjectProperty.name).forAllCommits { commits += it }
            val since = filter.sinceCommit?.let { sinceCommit -> commits.indexOfFirst { it.id == sinceCommit } } ?: -1
            commits.drop(since + 1).take(filter.count).forEach(code)
        }
    }

    class MockIssueServiceConfiguration : IssueServiceConfiguration {
        override val serviceId: String = "mock"
        override val name: String = "mock"

        companion object {
            val INSTANCE = MockIssueServiceConfiguration()
        }
    }

    inner class MockIssueServiceExtension(
        private val repositoryName: String,
    ) : IssueServiceExtension {

        override val id: String = "mock"

        override val name: String = "Mock issues"

        override fun getConfigurationList(): List<IssueServiceConfiguration> = emptyList()

        override fun getConfigurationByName(name: String): IssueServiceConfiguration? = null

        override fun getLastCommit(
            issueServiceConfiguration: IssueServiceConfiguration,
            repositoryContext: IssueRepositoryContext,
            key: String
        ): String? =
            repository(repositoryName).findIssue(key)?.lastCommitId()

        override fun extractIssueKeysFromMessage(
            issueServiceConfiguration: IssueServiceConfiguration,
            message: String?,
        ): Set<String> =
            issueRegex.findAll(message ?: "").map { m ->
                m.groupValues[1]
            }.toSet()

        override fun getMessageAnnotator(issueServiceConfiguration: IssueServiceConfiguration): MessageAnnotator {
            return RegexMessageAnnotator(issueRegex) { key: String? ->
                of("a")
                    .attr("href", "mock://$name/issue/$key")
                    .text(key)
            }
        }

        override fun getIssueTypes(
            issueServiceConfiguration: IssueServiceConfiguration,
            issue: Issue
        ): Set<String> = if (issue is MockIssue) {
            issue.types ?: emptySet()
        } else {
            emptySet()
        }

        override fun getIssue(issueServiceConfiguration: IssueServiceConfiguration, issueKey: String): Issue? =
            repository(repositoryName).findIssue(issueKey)

        override fun getIssueId(
            issueServiceConfiguration: IssueServiceConfiguration,
            token: String?
        ): String? {
            TODO("Not yet implemented")
        }

        override val feature: ExtensionFeature
            get() = TODO("Not yet implemented")

    }

    companion object {
        var issueRegex = "([A-Z]+-\\d+)".toRegex()
    }
}


