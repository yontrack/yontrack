package net.nemerosa.ontrack.extension.git.service

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.nemerosa.ontrack.common.FutureUtils
import net.nemerosa.ontrack.common.asOptional
import net.nemerosa.ontrack.extension.git.GitConfigProperties
import net.nemerosa.ontrack.extension.git.casc.GitConfigService
import net.nemerosa.ontrack.extension.git.model.*
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationProperty
import net.nemerosa.ontrack.extension.git.property.GitBranchConfigurationPropertyType
import net.nemerosa.ontrack.extension.git.repository.GitRepositoryHelper
import net.nemerosa.ontrack.extension.git.support.NoGitCommitPropertyException
import net.nemerosa.ontrack.git.GitRepositoryClient
import net.nemerosa.ontrack.git.GitRepositoryClientFactory
import net.nemerosa.ontrack.git.exceptions.GitRepositoryNoRemoteException
import net.nemerosa.ontrack.git.model.GitBranchInfo
import net.nemerosa.ontrack.git.model.GitCommit
import net.nemerosa.ontrack.git.model.GitSynchronisationStatus
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.orchestrator.JobOrchestratorSupplier
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.AbstractBranchJob
import net.nemerosa.ontrack.tx.TransactionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.lang.String.format
import java.util.concurrent.Future
import java.util.function.BiConsumer
import kotlin.jvm.optionals.getOrNull

@Service
@Transactional
class GitServiceImpl(
    private val structureService: StructureService,
    private val propertyService: PropertyService,
    private val jobScheduler: JobScheduler,
    private val securityService: SecurityService,
    private val transactionService: TransactionService,
    private val gitRepositoryClientFactory: GitRepositoryClientFactory,
    private val buildGitCommitLinkService: BuildGitCommitLinkService,
    private val gitConfigurators: Collection<GitConfigurator>,
    private val gitRepositoryHelper: GitRepositoryHelper,
    private val entityDataService: EntityDataService,
    private val gitConfigProperties: GitConfigProperties,
    private val gitPullRequestCache: DefaultGitPullRequestCache,
    private val gitNoRemoteCounter: GitNoRemoteCounter,
    private val gitConfigService: GitConfigService,
    transactionManager: PlatformTransactionManager
) : GitService, JobOrchestratorSupplier {

    private val logger = LoggerFactory.getLogger(GitService::class.java)

    private val transactionTemplate = TransactionTemplate(transactionManager)

    override fun forEachConfiguredProject(consumer: BiConsumer<Project, GitConfiguration>) {
        structureService.projectList
            .forEach { project ->
                val configuration = getProjectConfiguration(project)
                if (configuration != null) {
                    consumer.accept(project, configuration)
                }
            }
    }

    override fun forEachConfiguredBranch(consumer: BiConsumer<Branch, GitBranchConfiguration>) {
        for (project in structureService.projectList) {
            forEachConfiguredBranchInProject(project, consumer::accept)
        }
    }

    override fun forEachConfiguredBranchInProject(
        project: Project,
        consumer: (Branch, GitBranchConfiguration) -> Unit
    ) {
        structureService.getBranchesForProject(project.id)
            .forEach { branch ->
                val configuration = getBranchConfiguration(branch)
                if (configuration != null) {
                    consumer(branch, configuration)
                }
            }
    }

    override val jobRegistrations: Collection<JobRegistration>
        get() {
            val jobs = mutableListOf<JobRegistration>()
            // Indexation of repositories, based on projects actually linked
            forEachConfiguredProject { project, configuration ->
                if (!project.isDisabled) {
                    jobs.add(getGitIndexationJobRegistration(configuration, project))
                }
            }
            // Synchronisation of branch builds with tags when applicable
            forEachConfiguredBranch { branch, branchConfiguration ->
                if (!branch.isDisabled && !branch.project.isDisabled) {
                    // Build/tag sync job
                    if (branchConfiguration.buildTagInterval > 0 && branchConfiguration.buildCommitLink?.link is IndexableBuildGitCommitLink<*>) {
                        jobs.add(
                            JobRegistration.of(createBuildSyncJob(branch))
                                .everyMinutes(branchConfiguration.buildTagInterval.toLong())
                        )
                    }
                }
            }
            // OK
            return jobs
        }

    override fun isBranchConfiguredForGit(branch: Branch): Boolean {
        return getBranchConfiguration(branch) != null
    }

    override fun launchBuildSync(branchId: ID, synchronous: Boolean): Future<*>? {
        // Gets the branch
        val branch = structureService.getBranch(branchId)
        // Gets its configuration
        val branchConfiguration = getBranchConfiguration(branch)
        // If valid, launches a job
        return if (branchConfiguration != null && branchConfiguration.buildCommitLink?.link is IndexableBuildGitCommitLink<*>) {
            if (synchronous) {
                buildSync<Any>(branch, branchConfiguration, JobRunListener.logger(logger))
                null
            } else {
                jobScheduler.fireImmediately(getGitBranchSyncJobKey(branch)).orElse(null)
            }
        } else {
            null
        }
    }

    private fun syncAndWait(gitConfiguration: GitConfiguration): Any? {
        // Gets the sync job (might be null)
        val sync = sync(gitConfiguration, GitSynchronisationRequest.SYNC)
        return if (sync != null) {
            FutureUtils.wait("Synchronisation for " + gitConfiguration.name, sync)
        } else {
            null
        }
    }

    private fun getRequiredProjectConfiguration(project: Project): GitConfiguration {
        return getProjectConfiguration(project) ?: throw GitProjectNotConfiguredException(project.id)
    }

    override fun isPatternFound(gitConfiguration: GitConfiguration, token: String): Boolean {
        // Gets the client
        val client = gitRepositoryClientFactory.getClient(gitConfiguration.gitRepository, gitConfigService.gitConnectionConfig)
        // Scanning
        return client.isPatternFound(token)
    }

    override fun lookupCommit(configuration: GitConfiguration, id: String): GitCommit? {
        // Gets the client client for this configuration
        val gitClient = gitRepositoryClientFactory.getClient(configuration.gitRepository, gitConfigService.gitConnectionConfig)
        // Gets the commit
        return gitClient.getCommitFor(id)
    }

    override fun forEachCommit(gitConfiguration: GitConfiguration, code: (GitCommit) -> Unit) {
        // Gets the client for this configuration
        val gitClient = gitRepositoryClientFactory.getClient(gitConfiguration.gitRepository, gitConfigService.gitConnectionConfig)
        // Looping
        gitClient.forEachCommit(code)
    }

    override fun isRepositorySynched(gitConfiguration: GitConfiguration): Boolean {
        // Gets the client client for this configuration
        val gitClient = gitRepositoryClientFactory.getClient(gitConfiguration.gitRepository, gitConfigService.gitConnectionConfig)
        // Test
        return gitClient.isReady
    }

    override fun getRemoteBranches(gitConfiguration: GitConfiguration): List<String> {
        val gitClient = gitRepositoryClientFactory.getClient(gitConfiguration.gitRepository, gitConfigService.gitConnectionConfig)
        return gitClient.remoteBranches
    }

    override fun download(project: Project, scmBranch: String, path: String): String? {
        securityService.checkProjectFunction(project, ProjectConfig::class.java)
        return transactionService.doInTransaction {
            val projectConfiguration = getRequiredProjectConfiguration(project)
            val client = gitRepositoryClientFactory.getClient(
                projectConfiguration.gitRepository,
                gitConfigService.gitConnectionConfig
            )
            client.download(scmBranch, path).asOptional()
        }.getOrNull()
    }

    override fun projectSync(project: Project, request: GitSynchronisationRequest): Ack {
        securityService.checkProjectFunction(project, ProjectConfig::class.java)
        val projectConfiguration = getProjectConfiguration(project)
        if (projectConfiguration != null) {
            val sync = sync(projectConfiguration, request)
            return Ack.validate(sync != null)
        } else {
            return Ack.NOK
        }
    }

    override fun sync(gitConfiguration: GitConfiguration, request: GitSynchronisationRequest): Future<*>? {
        // Reset the repository?
        if (request.isReset) {
            gitRepositoryClientFactory.getClient(gitConfiguration.gitRepository, gitConfigService.gitConnectionConfig).reset()
        }
        // Schedules the job
        return jobScheduler.fireImmediately(getGitIndexationJobKey(gitConfiguration)).orElse(null)
    }

    override fun getProjectGitSyncInfo(project: Project): GitSynchronisationInfo {
        securityService.checkProjectFunction(project, ProjectConfig::class.java)
        return getProjectConfiguration(project)
            ?.let { getGitSynchronisationInfo(it) }
            ?: throw GitProjectNotConfiguredException(project.id)
    }

    private fun getGitSynchronisationInfo(gitConfiguration: GitConfiguration): GitSynchronisationInfo {
        // Gets the client for this configuration
        val client = gitRepositoryClientFactory.getClient(gitConfiguration.gitRepository, gitConfigService.gitConnectionConfig)
        // Gets the status
        val status = client.synchronisationStatus
        // Collects the branch info
        val branches: List<GitBranchInfo> = if (status == GitSynchronisationStatus.IDLE) {
            client.branches.branches
        } else {
            emptyList()
        }
        // OK
        return GitSynchronisationInfo(
            gitConfiguration.type,
            gitConfiguration.name,
            gitConfiguration.remote,
            gitConfiguration.indexationInterval,
            status,
            branches
        )
    }

    override fun getGitConfiguratorAndConfiguration(project: Project): Pair<GitConfigurator, GitConfiguration>? =
        gitConfigurators.firstNotNullOfOrNull {
            val configuration = it.getConfiguration(project)
            if (configuration != null) {
                it to configuration
            } else {
                null
            }
        }

    override fun isProjectConfiguredForGit(project: Project): Boolean =
        gitConfigurators.any { configurator ->
            configurator.isProjectConfigured(project)
        }

    override fun getProjectConfiguration(project: Project): GitConfiguration? {
        return gitConfigurators.asSequence()
            .mapNotNull { c -> c.getConfiguration(project) }
            .firstOrNull()
    }

    private fun getRequiredBranchConfiguration(branch: Branch): GitBranchConfiguration {
        return getBranchConfiguration(branch)
            ?: throw GitBranchNotConfiguredException(branch.id)
    }

    override fun getBranchAsPullRequest(branch: Branch): GitPullRequest? =
        propertyService.getProperty(branch, GitBranchConfigurationPropertyType::class.java).value?.let { property ->
            getBranchAsPullRequest(branch, property)
        }

    override fun isBranchAPullRequest(branch: Branch): Boolean =
        gitConfigProperties.pullRequests.enabled &&
                propertyService.getProperty(branch, GitBranchConfigurationPropertyType::class.java).value
                    ?.let { property ->
                        getGitConfiguratorAndConfiguration(branch.project)
                            ?.let { (configurator, _) ->
                                configurator.toPullRequestID(property.branch) != null
                            }
                    }
                ?: false

    override fun getBranchAsPullRequest(
        branch: Branch,
        gitBranchConfigurationProperty: GitBranchConfigurationProperty?
    ): GitPullRequest? =
        if (gitConfigProperties.pullRequests.enabled) {
            // Actual code to get the PR
            fun internalPR() = gitBranchConfigurationProperty?.let {
                getGitConfiguratorAndConfiguration(branch.project)
                    ?.let { (configurator, configuration) ->
                        configurator.toPullRequestID(gitBranchConfigurationProperty.branch)?.let { prId ->
                            try {
                                configurator.getPullRequest(configuration, prId)
                                    ?: GitPullRequest.invalidPR(prId, configurator.toPullRequestKey(prId))
                            } catch (any: Exception) {
                                logger.error(
                                    "Error while getting PR info for ${branch.entityDisplayName}",
                                    any
                                )
                                // Not returning a PR
                                null
                            }
                        }
                    }
            }
            // Calling the cache
            runBlocking {
                withTimeoutOrNull(timeMillis = gitConfigProperties.pullRequests.timeout.toMillis()) {
                    gitPullRequestCache.getBranchPullRequest(branch, ::internalPR)
                }
            }
        } else {
            // Pull requests are not supported
            null
        }

    override fun getBranchConfiguration(branch: Branch): GitBranchConfiguration? {
        // Get the configuration for the project
        val configuration = getProjectConfiguration(branch.project)
        if (configuration != null) {
            // Gets the configuration for a branch
            val gitBranch: String
            val buildCommitLink: ConfiguredBuildGitCommitLink<*>?
            val override: Boolean
            val buildTagInterval: Int
            val branchConfigurationProperty = propertyService.getPropertyValue(branch, GitBranchConfigurationPropertyType::class.java)
            if (branchConfigurationProperty != null) {
                gitBranch = branchConfigurationProperty.branch
                buildCommitLink = branchConfigurationProperty.buildCommitLink?.let {
                    toConfiguredBuildGitCommitLink<Any>(it)
                }
                override = branchConfigurationProperty.override
                buildTagInterval = branchConfigurationProperty.buildTagInterval
                // OK
                return GitBranchConfiguration(
                    configuration,
                    gitBranch,
                    buildCommitLink,
                    override,
                    buildTagInterval
                )
            } else {
                return null
            }
        } else {
            return null
        }
    }

    override fun findBranchWithGitBranch(project: Project, branchName: String): Branch? {
        return gitRepositoryHelper.findBranchWithProjectAndGitBranch(project, branchName)
            ?.let { structureService.getBranch(ID.of(it)) }
    }

    private fun <T> toConfiguredBuildGitCommitLink(serviceConfiguration: ServiceConfiguration): ConfiguredBuildGitCommitLink<T> {
        @Suppress("UNCHECKED_CAST")
        val link = buildGitCommitLinkService.getLink(serviceConfiguration.id) as BuildGitCommitLink<T>
        val linkData = link.parseData(serviceConfiguration.data)
        return ConfiguredBuildGitCommitLink(
            link,
            linkData
        )
    }

    private fun createBuildSyncJob(branch: Branch): Job {
        val configuration = getRequiredBranchConfiguration(branch)
        return object : AbstractBranchJob(structureService, branch) {

            override fun getKey(): JobKey {
                return getGitBranchSyncJobKey(branch)
            }

            override fun getTask(): JobRun {
                return JobRun { listener -> buildSync<Any>(branch, configuration, listener) }
            }

            override fun getDescription(): String {
                return format(
                    "Branch %s @ %s",
                    branch.name,
                    branch.project.name
                )
            }

            override fun isDisabled(): Boolean {
                return super.isDisabled() && isBranchConfiguredForGit(branch)
            }
        }
    }

    private fun getGitBranchSyncJobKey(branch: Branch): JobKey {
        return GIT_BUILD_SYNC_JOB.getKey(branch.id.toString())
    }

    private fun getGitIndexationJobKey(config: GitConfiguration): JobKey {
        return GIT_INDEXATION_JOB.getKey(config.gitRepository.id)
    }

    private fun createIndexationJob(config: GitConfiguration, project: Project): Job {
        return object : Job {
            override fun getKey(): JobKey {
                return getGitIndexationJobKey(config)
            }

            override fun getTask(): JobRun {
                return JobRun { runListener -> index(config, project, runListener) }
            }

            override fun getDescription(): String {
                return format(
                    "%s (%s @ %s)",
                    config.remote,
                    config.name,
                    config.type
                )
            }

            override fun isDisabled(): Boolean {
                return false
            }

            override fun getTimeout() = gitConfigProperties.indexation.timeout
        }
    }

    private fun <T> buildSync(branch: Branch, branchConfiguration: GitBranchConfiguration, listener: JobRunListener) {
        listener.message("Git build/tag sync for %s/%s", branch.project.name, branch.name)
        val configuration = branchConfiguration.configuration
        // Gets the branch Git client
        val gitClient = gitRepositoryClientFactory.getClient(configuration.gitRepository, gitConfigService.gitConnectionConfig)
        // Link
        @Suppress("UNCHECKED_CAST")
        val link = branchConfiguration.buildCommitLink?.link as IndexableBuildGitCommitLink<T>?

        @Suppress("UNCHECKED_CAST")
        val linkData = branchConfiguration.buildCommitLink?.data as T?
        // Check for configuration
        if (link == null || linkData == null) {
            listener.message("No commit link configuration on the branch - no synchronization.")
            return
        }
        // Configuration for the sync
        val confProperty = propertyService.getPropertyValue(branch, GitBranchConfigurationPropertyType::class.java)
        val override = confProperty != null && confProperty.override
        // Makes sure of synchronization
        listener.message("Synchronizing before importing")
        syncAndWait(configuration)
        // Gets the list of tags
        listener.message("Getting list of tags")
        val tags = gitClient.tags
        // Creates the builds
        listener.message("Creating builds from tags")
        for (tag in tags) {
            val tagName = tag.name
            // Filters the tags according to the branch tag pattern
            link.getBuildNameFromTagName(tagName, linkData)?.let { buildNameCandidate ->
                val buildName = NameDescription.escapeName(buildNameCandidate)
                listener.message(format("Build %s from tag %s", buildName, tagName))
                // Existing build?
                val build = structureService.findBuildByName(branch.project.name, branch.name, buildName)
                val createBuild: Boolean = if (build.isPresent) {
                    if (override) {
                        // Deletes the build
                        listener.message("Deleting existing build %s", buildName)
                        structureService.deleteBuild(build.get().id)
                        true
                    } else {
                        // Keeps the build
                        listener.message("Build %s already exists", buildName)
                        false
                    }
                } else {
                    true
                }
                // Actual creation
                if (createBuild) {
                    listener.message("Creating build %s from tag %s", buildName, tagName)
                    structureService.newBuild(
                        Build.of(
                            branch,
                            NameDescription(
                                buildName,
                                "Imported from Git tag $tagName"
                            ),
                            securityService.currentSignature.withTime(
                                tag.time
                            )
                        )
                    )
                }
            }
        }
    }

    private fun index(config: GitConfiguration, project: Project, listener: JobRunListener) {
        syncProjectRepository(config, project, listener::message)
    }

    override fun syncProjectRepository(
        config: GitConfiguration,
        project: Project,
        listener: (message: String) -> Unit
    ) {
        listener("Git sync for ${config.name}")
        // Gets the client for this configuration
        val client = gitRepositoryClientFactory.getClient(config.gitRepository, gitConfigService.gitConnectionConfig)
        // Launches the synchronisation
        try {
            client.sync {
                listener(it)
            }
            // Reset the counter for the project
            gitNoRemoteCounter.resetNoRemoteCount(project.name)
        } catch (ex: GitRepositoryNoRemoteException) {
            // Remote was mentioned as not existing
            if (gitConfigProperties.remote.maxNoRemote > 0) {
                // Gets the counter for the project
                val count = gitNoRemoteCounter.getNoRemoteCount(project.name)
                // If < threshold, just increment the counter
                if (count < gitConfigProperties.remote.maxNoRemote) {
                    gitNoRemoteCounter.incNoRemoteCount(project.name)
                } else {
                    // If >= threshold, disable the project and logs the incident
                    securityService.asAdmin {
                        structureService.disableProject(project)
                    }
                    logger.info("Indexation of Git repository for project ${project.name} failed because of no remote ${gitConfigProperties.remote.maxNoRemote} times in a row. Disabling the project.")
                }
            } else {
                throw ex
            }
        }
    }

    private fun getGitIndexationJobRegistration(configuration: GitConfiguration, project: Project): JobRegistration {
        return JobRegistration
            .of(createIndexationJob(configuration, project))
            .everyMinutes(configuration.indexationInterval.toLong())
    }

    override fun scheduleGitBuildSync(branch: Branch, property: GitBranchConfigurationProperty) {
        if (property.buildTagInterval > 0) {
            jobScheduler.schedule(
                createBuildSyncJob(branch),
                Schedule.everyMinutes(property.buildTagInterval.toLong())
            )
        } else {
            unscheduleGitBuildSync(branch, property)
        }
    }

    override fun unscheduleGitBuildSync(branch: Branch, property: GitBranchConfigurationProperty) {
        jobScheduler.unschedule(getGitBranchSyncJobKey(branch))
    }

    override fun getCommitForBuild(build: Build): IndexableGitCommit? =
        entityDataService.retrieve(
            build,
            "git-commit",
            IndexableGitCommit::class.java
        )

    override fun setCommitForBuild(build: Build, commit: IndexableGitCommit) {
        entityDataService.store(
            build,
            "git-commit",
            commit
        )
    }

    override fun collectIndexableGitCommitForBranch(branch: Branch, overrides: Boolean) {
        val project = branch.project
        val projectConfiguration = getProjectConfiguration(project)
        if (projectConfiguration != null) {
            val client = gitRepositoryClientFactory.getClient(projectConfiguration.gitRepository, gitConfigService.gitConnectionConfig)
            val branchConfiguration = getBranchConfiguration(branch)
            if (branchConfiguration != null) {
                collectIndexableGitCommitForBranch(
                    branch,
                    client,
                    branchConfiguration,
                    overrides,
                    JobRunListener.logger(logger)
                )
            }
        }
    }

    override fun collectIndexableGitCommitForBranch(
        branch: Branch,
        client: GitRepositoryClient,
        config: GitBranchConfiguration,
        overrides: Boolean,
        listener: JobRunListener
    ) {
        val buildCommitLink = config.buildCommitLink
        if (buildCommitLink != null) {
            structureService.findBuild(branch.id, BuildSortDirection.FROM_NEWEST) { build ->
                collectIndexableGitCommitForBuild(build, client, buildCommitLink, overrides, listener) ?: false
            }
        }
    }

    private fun collectIndexableGitCommitForBuild(
        build: Build,
        client: GitRepositoryClient,
        buildCommitLink: ConfiguredBuildGitCommitLink<*>,
        overrides: Boolean,
        listener: JobRunListener
    ): Boolean = transactionTemplate.execute {
        val commit =
            try {
                buildCommitLink.getCommitFromBuild(build)
            } catch (_: NoGitCommitPropertyException) {
                null
            }
        if (commit != null) {
            listener.message("Indexing $commit for build ${build.entityDisplayName}")
            // Gets the Git information for the commit
            val toSet: Boolean = overrides || getCommitForBuild(build) == null
            if (toSet) {
                val commitFor = client.getCommitFor(commit)
                if (commitFor != null) {
                    setCommitForBuild(build, IndexableGitCommit(commitFor))
                }
            }
        }
        // Going on
        false
    } ?: false

    companion object {
        private val GIT_INDEXATION_JOB = GIT_JOB_CATEGORY.getType("git-indexation").withName("Git indexation")
        private val GIT_BUILD_SYNC_JOB =
            GIT_JOB_CATEGORY.getType("git-build-sync").withName("Git build synchronisation")
    }
}
