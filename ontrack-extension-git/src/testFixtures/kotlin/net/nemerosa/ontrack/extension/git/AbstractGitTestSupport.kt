package net.nemerosa.ontrack.extension.git

import net.nemerosa.ontrack.extension.git.casc.GitConfigService
import net.nemerosa.ontrack.extension.git.mocking.GitMockingConfigurationProperty
import net.nemerosa.ontrack.extension.git.mocking.GitMockingConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.mocking.LocalGitConfiguration
import net.nemerosa.ontrack.extension.git.mocking.LocalGitProjectConfigurationProperty
import net.nemerosa.ontrack.extension.git.mocking.LocalGitProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.model.ConfiguredBuildGitCommitLink
import net.nemerosa.ontrack.extension.git.model.gitRepository
import net.nemerosa.ontrack.extension.git.property.*
import net.nemerosa.ontrack.extension.git.service.GitService
import net.nemerosa.ontrack.extension.git.support.*
import net.nemerosa.ontrack.extension.issues.mock.TestIssueServiceConfiguration
import net.nemerosa.ontrack.extension.issues.model.toIdentifier
import net.nemerosa.ontrack.extension.scm.model.BranchInfo
import net.nemerosa.ontrack.extension.scm.support.TagPattern
import net.nemerosa.ontrack.git.GitRepositoryClientFactory
import net.nemerosa.ontrack.git.support.GitRepo
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.job.JobRunListener
import net.nemerosa.ontrack.job.orchestrator.JobOrchestrator
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.NoConfig
import net.nemerosa.ontrack.test.TestUtils
import org.springframework.beans.factory.annotation.Autowired
import java.lang.Thread.sleep
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@AsAdminTest
abstract class AbstractGitTestSupport : AbstractQLKTITSupport() {

    @Autowired
    protected lateinit var gitConfigProperties: GitConfigProperties

    @Autowired
    private lateinit var commitBuildNameGitCommitLink: CommitBuildNameGitCommitLink

    @Autowired
    private lateinit var gitCommitPropertyCommitLink: GitCommitPropertyCommitLink

    @Autowired
    private lateinit var tagBuildNameGitCommitLink: TagBuildNameGitCommitLink

    @Autowired
    private lateinit var tagPatternBuildNameGitCommitLink: TagPatternBuildNameGitCommitLink

    @Autowired
    private lateinit var gitRepositoryClientFactory: GitRepositoryClientFactory

    @Autowired
    protected lateinit var gitService: GitService

    @Autowired
    protected lateinit var gitConfigService: GitConfigService

    @Autowired
    private lateinit var jobOrchestrator: JobOrchestrator

    /**
     * Working with the PR cache disabled
     */
    protected fun <T> withPRCacheDisabled(code: () -> T): T {
        val old = gitConfigProperties.pullRequests.cache.enabled
        return try {
            gitConfigProperties.pullRequests.cache.enabled = false
            code()
        } finally {
            gitConfigProperties.pullRequests.cache.enabled = old
        }
    }

    /**
     * Working with PR feature disabled
     */
    protected fun <T> withPRDisabled(code: () -> T): T {
        val old = gitConfigProperties.pullRequests.enabled
        return try {
            gitConfigProperties.pullRequests.enabled = false
            code()
        } finally {
            gitConfigProperties.pullRequests.enabled = old
        }
    }

    /**
     * Working with PR cleanup feature disabled
     */
    protected fun <T> withPRCleanupDisabled(code: () -> T): T {
        val old = gitConfigProperties.pullRequests.cleanup.enabled
        return try {
            gitConfigProperties.pullRequests.cleanup.enabled = false
            code()
        } finally {
            gitConfigProperties.pullRequests.cleanup.enabled = old
        }
    }

    /**
     * Working with custom PR cleanup properties
     */
    protected fun <T> withPRCleanupProperties(disabling: Int? = null, deleting: Int? = null, code: () -> T): T {
        val oldDisabling = gitConfigProperties.pullRequests.cleanup.disabling
        val oldDeleting = gitConfigProperties.pullRequests.cleanup.deleting
        return try {
            disabling?.apply { gitConfigProperties.pullRequests.cleanup.disabling = this }
            deleting?.apply { gitConfigProperties.pullRequests.cleanup.deleting = this }
            code()
        } finally {
            gitConfigProperties.pullRequests.cleanup.disabling = oldDisabling
            gitConfigProperties.pullRequests.cleanup.deleting = oldDeleting
        }
    }

    /**
     * Creates a configuration for a local Git repository, synchronising its clone if asked to.
     */
    protected fun createGitConfiguration(repo: GitRepo, sync: Boolean = true): LocalGitProjectConfigurationProperty {
        val property = LocalGitProjectConfigurationProperty(
            name = TestUtils.uid("C"),
            remote = "file://${repo.dir.absolutePath}",
            issueServiceConfigurationIdentifier = TestIssueServiceConfiguration.INSTANCE.toIdentifier().format(),
        )
        if (sync) {
            val configuration = LocalGitConfiguration(property.name, property.remote, null)
            gitRepositoryClientFactory.getClient(configuration.gitRepository, gitConfigService.gitConnectionConfig).sync { println(it) }
        }
        return property
    }

    /**
     * Configures a project for Git, on a local repository.
     */
    protected fun Project.gitProject(repo: GitRepo, sync: Boolean = true) {
        // Configures the project
        setProperty(
            this,
            LocalGitProjectConfigurationPropertyType::class.java,
            createGitConfiguration(repo, sync)
        )
        // Makes sure to register the project
        if (sync) {
            asAdmin().execute {
                jobOrchestrator.orchestrate(JobRunListener.out())
            }
        }
    }

    /**
     * Configures a project for Git, with compatibility with pull requests (mocking)
     */
    protected fun Project.prGitProject(sync: Boolean = true) {
        // Configures the project
        setProperty(
            this,
            GitMockingConfigurationPropertyType::class.java,
            GitMockingConfigurationProperty(null)
        )
        // Makes sure to register the project
        if (sync) {
            asAdmin().execute {
                jobOrchestrator.orchestrate(JobRunListener.out())
            }
        }
    }

    /**
     * Configures a branch for Git.
     *
     * @receiver Branch to configure
     * @param branchName Git branch to associate with the branch
     * @param commitLinkConfiguration Returns the build commit link, defaults to [buildNameAsCommit]
     */
    protected fun Branch.gitBranch(
        branchName: String = "main",
        commitLinkConfiguration: () -> ConfiguredBuildGitCommitLink<*> = { buildNameAsCommit() }
    ) {
        asAdmin().execute {
            propertyService.editProperty(
                this,
                GitBranchConfigurationPropertyType::class.java,
                GitBranchConfigurationProperty(
                    branchName,
                    commitLinkConfiguration().toServiceConfiguration(),
                    false, 0
                )
            )
        }
    }

    /**
     * Configuration of a build commit link based on build name being a commit
     */
    protected fun buildNameAsCommit(abbreviated: Boolean = true): ConfiguredBuildGitCommitLink<CommitLinkConfig> {
        return ConfiguredBuildGitCommitLink(
            commitBuildNameGitCommitLink,
            CommitLinkConfig(abbreviated)
        )
    }

    /**
     * Configuration of a build commit link based on a commit property.
     */
    protected fun commitAsProperty() = ConfiguredBuildGitCommitLink(
        gitCommitPropertyCommitLink,
        NoConfig.INSTANCE
    )

    /**
     * Configuration of a build commit link based on tag as build name.
     */
    protected fun tagBuildName() = ConfiguredBuildGitCommitLink(
        tagBuildNameGitCommitLink,
        NoConfig.INSTANCE
    )

    /**
     * Configuration of a build commit link based on tag pattern as build name.
     */
    protected fun tagPatternBuildName(pattern: String) = ConfiguredBuildGitCommitLink(
        tagPatternBuildNameGitCommitLink,
        TagPattern(pattern)
    )

    /**
     * Sets the Git commit property on a build
     */
    protected fun Build.gitCommitProperty(commit: String) {
        setProperty(
            this,
            GitCommitPropertyType::class.java,
            GitCommitProperty(commit)
        )
    }

    /**
     * Creates [n] commits, from 1 to [n], with message being "Commit `i`" by default.
     *
     * @param n Number of commits to create
     * @param pauses `true` if we must pause ~ 1 second between each commit (default to `false`)
     * @param shortId `true` if we must return the short commit hash, not the long ones (default to `false`)
     * @return A map where the key in the index, and the value is the commit hash.
     */
    protected fun GitRepo.commits(n: Int, pauses: Boolean = false, shortId: Boolean = false) =
        (1..n).associate {
            val message = "Commit $it"
            commit(it, message)
            val hash = commitLookup(message, shortId)
            if (pauses) sleep(1010)
            it to hash
        }

    /**
     * Creates a sequence of commits on different branches.
     */
    protected fun GitRepo.sequence(vararg commands: Any): Map<Int, String> =
        runSequence(commands.toList(), false)

    /**
     * Creates a sequence of commits on different branches, pausing after each commit
     */
    protected fun GitRepo.sequenceWithPauses(vararg commands: Any): Map<Int, String> =
        runSequence(commands.toList(), true)

    /**
     * Creates a sequence of commits on different branches.
     */
    private fun GitRepo.runSequence(commands: List<*>, pauses: Boolean): Map<Int, String> {
        val index = mutableMapOf<Int, String>()
        val branches = mutableSetOf("main")
        commands.forEach { command ->
            when (command) {
                // Branch
                is String -> branch(command, branches)
                // Single commit
                is Int -> {
                    index[command] = commit(command)
                    if (pauses) sleep(1010)
                }
                // Range of commit
                is IntRange -> command.forEach {
                    index[it] = commit(it)
                    if (pauses) sleep(1010)
                }
                // Commit to tag
                is Pair<*, *> -> {
                    val commit = command.first as Int
                    val tag = command.second as String
                    index[commit] = commit(commit)
                    tag(tag)
                    if (pauses) sleep(1010)
                }
                // Any other item
                else -> throw IllegalArgumentException("Unknown type: $command")
            }
        }
        return index
    }

    private fun GitRepo.branch(branch: String, branches: MutableSet<String>) {
        if (branches.contains(branch)) {
            git("checkout", branch)
        } else {
            branches += branch
            git("checkout", "-b", branch)
        }
    }

    protected fun withRepo(code: (GitRepo) -> Unit) {
        createRepo { commits(1) } and { repo, _ -> code(repo) }
    }

    protected fun <T> createRepo(init: GitRepo.() -> T) = RepoTestActions(init)

    protected class RepoTestActions<T>(
        private val init: GitRepo.() -> T
    ) {
        infix fun and(code: (GitRepo, T) -> Unit) {
            var value: T? = null
            GitRepo.prepare {
                gitInit()
                value = init()
                log()
            } and { _, repo ->
                repo.use {
                    code(it, value!!)
                }
            }
        }
    }

    protected class BranchInfoTest(
        private val branch: String,
        private val firstBuild: String?,
        private val promotions: List<Pair<String, String>> = emptyList()
    ) {
        operator fun invoke(branchInfo: BranchInfo) {
            // Branch
            assertEquals(branch, branchInfo.branch.name)
            // First build test
            if (firstBuild != null) {
                assertNotNull(branchInfo.firstBuild, "First build expected") {
                    assertEquals(firstBuild, it.name)
                }
            } else {
                assertNull(branchInfo.firstBuild, "No first build")
            }
            // Promotion tests
            assertEquals(promotions.size, branchInfo.promotions.size)
            branchInfo.promotions.zip(promotions).forEach { (run, promotionTest) ->
                val promotion = promotionTest.first
                val name = promotionTest.second
                assertEquals(promotion, run.promotionLevel.name)
                assertEquals(name, run.build.name)
            }
        }
    }

    protected fun Branch.build(
        no: Int,
        commits: Map<Int, String>,
        validations: List<ValidationStamp> = emptyList(),
        promotions: List<PromotionLevel> = emptyList()
    ) {
        build(no.toString()) {
            gitCommitProperty(commits.getValue(no))
            validations.forEach { validate(it) }
            promotions.forEach { promote(it) }
            gitService.getCommitForBuild(this)?.let {
                println("build=$entityDisplayName,commit=${it.commit.shortId},time=${it.commit.commitTime},timestamp=${it.timestamp}")
            }
        }
    }

}