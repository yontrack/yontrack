package net.nemerosa.ontrack.extension.git.service

import net.nemerosa.ontrack.extension.git.model.*
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationProperty
import net.nemerosa.ontrack.git.GitRepositoryClient
import net.nemerosa.ontrack.git.model.GitCommit
import net.nemerosa.ontrack.job.JobCategory
import net.nemerosa.ontrack.job.JobRunListener
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import java.util.concurrent.Future
import java.util.function.BiConsumer

val GIT_JOB_CATEGORY = JobCategory.of("git").withName("Git")

interface GitService {

    /**
     * Gets the Git configurator for a project
     */
    fun getGitConfiguratorAndConfiguration(project: Project): Pair<GitConfigurator, GitConfiguration>?

    /**
     * Tests if a project is correctly configured for Git.
     */
    fun isProjectConfiguredForGit(project: Project): Boolean

    /**
     * Tests if a branch is correctly configured for Git.
     */
    fun isBranchConfiguredForGit(branch: Branch): Boolean

    /**
     * Gets the configuration for a project.
     */
    fun getProjectConfiguration(project: Project): GitConfiguration?

    /**
     * Gets (if any) the pull request associated to a branch.
     *
     * Note that [GitService.getBranchAsPullRequest] must be used instead if you happen
     * to already have some information about the branch.
     *
     * @param branch Branch
     * @return Pull request or null is none
     */
    fun getBranchAsPullRequest(branch: Branch): GitPullRequest?

    /**
     * Is this branch a potential pull request?
     *
     * This method won't check if the branch associated pull request actually exists.
     *
     * @param branch Branch
     * @return Pull request status
     */
    fun isBranchAPullRequest(branch: Branch): Boolean

    /**
     * Given a branch and its Git configuration, gets its PR information.
     *
     * @param branch Branch
     * @param gitBranchConfigurationProperty Gt branch configuration
     * @return Pull request or null is none
     */
    fun getBranchAsPullRequest(
        branch: Branch,
        gitBranchConfigurationProperty: GitBranchConfigurationProperty?
    ): GitPullRequest?

    /**
     * Gets the configuration for a branch
     *
     * @param branch Branch to check
     * @return Configuration.
     */
    fun getBranchConfiguration(branch: Branch): GitBranchConfiguration?

    /**
     * Gets an Ontrack branch for a given Git branch inside an Ontrack project.
     *
     * @param project Project where to look for the branch
     * @param branchName Name of the Git branch
     * @return Ontrack branch or `null` if not found
     */
    fun findBranchWithGitBranch(project: Project, branchName: String): Branch?

    /**
     * Launches the build/tag synchronisation for a branch
     */
    fun launchBuildSync(branchId: ID, synchronous: Boolean): Future<*>?

    /**
     * Loops over each correctly configured project.
     */
    fun forEachConfiguredProject(consumer: BiConsumer<Project, GitConfiguration>)

    /**
     * Loops over each correctly configured branch. Branch template definitions are excluded.
     */
    fun forEachConfiguredBranch(consumer: BiConsumer<Branch, GitBranchConfiguration>)


    /**
     * Loops over each correctly configured branch in a project. Branch template definitions are excluded.
     */
    fun forEachConfiguredBranchInProject(project: Project, consumer: (Branch, GitBranchConfiguration) -> Unit)

    /**
     * Looks up a commit in the given `configuration`.
     *
     * @param id Commit long or short ID
     * @return The content of a commit if it exists, `null` otherwise.
     */
    fun lookupCommit(configuration: GitConfiguration, id: String): GitCommit?

    /**
     * Downloads the file at the given path for a branch
     *
     * @param project Project holding the Git configuration
     * @param scmBranch Name of the Git branch
     * @param path Path to the file, relative to the repository
     * @return Content of the file or null if not found
     */
    fun download(project: Project, scmBranch: String, path: String): String?

    /**
     * Gets the list of remote branches, as defined under `ref/heads`.
     */
    fun getRemoteBranches(gitConfiguration: GitConfiguration): List<String>

    /**
     * Synchronises the Git repository attached to this project.
     *
     * @param project Project
     * @param request Sync request
     * @return Result. The synchronisation will occur asynchronously, but the acknowledgment returns
     * if the project did contain a Git configuration or not.
     */
    fun projectSync(project: Project, request: GitSynchronisationRequest): Ack

    /**
     * Synchronises the Git repository attached to this configuration using an asynchronous job.
     *
     * @param gitConfiguration Configuration to sync
     * @param request          Sync request
     * @return Result. The synchronisation will occur asynchronously, but the acknowledgment returns if the
     * synchronisation was actually launched.
     */
    fun sync(gitConfiguration: GitConfiguration, request: GitSynchronisationRequest): Future<*>?

    /**
     * Synchronises the Git repository attached to this configuration in a synchronous way. Same than [sync]
     * but without using a background job.
     *
     * This method is mostly used for testing.
     *
     * @param config Configuration to sync
     * @param project Associated project
     * @param listener Logger
     */
    fun syncProjectRepository(
        config: GitConfiguration,
        project: Project,
        listener: (message: String) -> Unit = { println(it) }
    )

    /**
     * Gets the Git synchronisation information.
     *
     * @param project Project configured for Git
     * @return Synchronisation information
     */
    fun getProjectGitSyncInfo(project: Project): GitSynchronisationInfo

    fun scheduleGitBuildSync(branch: Branch, property: GitBranchConfigurationProperty)

    fun unscheduleGitBuildSync(branch: Branch, property: GitBranchConfigurationProperty)

    /**
     * Checks the log history and returns `true` if the token can be found.
     *
     * @param gitConfiguration Git configuration
     * @param token               Expression to be searched for
     * @return Result of the search
     */
    fun isPatternFound(gitConfiguration: GitConfiguration, token: String): Boolean

    /**
     * Gets the [GitCommit] associated to a build
     *
     * @param build Build to get the information for
     * @return Commit information or `null` if not found
     */
    fun getCommitForBuild(build: Build): IndexableGitCommit?

    /**
     * Sets the [GitCommit] information for a build.
     *
     * @param build Build to set the information for
     * @param commit Commit information
     */
    fun setCommitForBuild(build: Build, commit: IndexableGitCommit)

    /**
     * Collects and stores the [IndexableGitCommit]s for all builds of a branch.
     */
    fun collectIndexableGitCommitForBranch(branch: Branch, overrides: Boolean = true)

    /**
     * Collects and stores the [IndexableGitCommit]s for all builds of a branch.
     *
     * This is the optimized version for jobs running in the background.
     */
    fun collectIndexableGitCommitForBranch(
        branch: Branch,
        client: GitRepositoryClient,
        config: GitBranchConfiguration,
        overrides: Boolean,
        listener: JobRunListener
    )

    /**
     * Loops over the commits of a configuration
     */
    fun forEachCommit(gitConfiguration: GitConfiguration, code: (GitCommit) -> Unit)

    /**
     * Checks if the repository is ready to be used.
     */
    fun isRepositorySynched(gitConfiguration: GitConfiguration): Boolean

}
