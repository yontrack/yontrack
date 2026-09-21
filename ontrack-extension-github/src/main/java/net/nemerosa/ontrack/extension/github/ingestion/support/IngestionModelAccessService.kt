package net.nemerosa.ontrack.extension.github.ingestion.support

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.github.ingestion.processing.events.WorkflowRun
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.IPullRequest
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.Repository
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.model.structure.*

/**
 * Service used to access the model resources (project, branch) when dealing with
 * the ingestion.
 */
interface IngestionModelAccessService {

    /**
     * Gets the GitHub configuration for a given repository.
     */
    fun findGitHubEngineConfiguration(
        repository: Repository,
        configurationName: String?,
    ): GitHubEngineConfiguration

    /**
     * Gets or creates a project for the given repository.
     *
     * @param repository Repository
     * @param configuration GitHub configuration name
     */
    fun getOrCreateProject(
        repository: Repository,
        configuration: String?,
    ): Project

    /**
     * Gets or creates a branch for the given [project].
     *
     * @param project Parent project
     * @param headBranch Git branch
     * @param pullRequest Pull request (if any)
     */
    fun getOrCreateBranch(
        project: Project,
        headBranch: String,
        pullRequest: IPullRequest?,
    ): Branch

    /**
     * Gets a branch from a payload if it exists.
     *
     * @param repository Repository
     * @param headBranch Git branch
     * @param pullRequest Pull request (if any)
     */
    fun getBranchIfExists(
        repository: Repository,
        headBranch: String,
        pullRequest: IPullRequest?,
    ): Branch?

    /**
     * Finds a project from a repository.
     */
    fun findProjectFromRepository(repository: Repository): Project?

    /**
     * Finds a branch from a Git reference
     */
    fun findBranchByRef(project: Project,
                        ref: String,
                        pullRequest: IPullRequest?,): Branch?

    /**
     * Sets a workflow run ID on a build.
     *
     * @param build Build to set
     * @param workflowRun Run to register
     */
    fun setBuildRunId(
        build: Build,
        workflowRun: WorkflowRun,
    )

    /**
     * Finds a build using its workflow run ID.
     */
    fun findBuildByRunId(
        repository: Repository,
        runId: Long,
    ): Build?

    /**
     * Finds a build using its name.
     */
    fun findBuildByBuildName(
        repository: Repository,
        buildName: String,
    ): Build?

    /**
     * Finds a build using its release property (label).
     */
    fun findBuildByBuildLabel(
        repository: Repository,
        buildLabel: String,
    ): Build?

    /**
     * Gets or creates a validation stamp
     *
     * @param branch Parent branch
     * @param vsName Name of the validation stamp
     * @param vsDescription Description of the validation stamp
     * @param dataType FQCN or shortcut of the data type for this validation stamp
     * @param dataTypeConfig JSON configuration for the data type
     * @param image Image reference
     */
    fun setupValidationStamp(
        branch: Branch,
        vsName: String,
        vsDescription: String?,
        dataType: String? = null,
        dataTypeConfig: JsonNode? = null,
        image: String? = null,
    ): ValidationStamp

    /**
     * Gets or creates a promotion level
     *
     * @param branch Parent branch
     * @param plName Name of the promotion level
     * @param plDescription Description of the promotion level
     * @param image Image reference
     */
    fun setupPromotionLevel(
        branch: Branch,
        plName: String,
        plDescription: String?,
        image: String? = null,
    ): PromotionLevel
}

/**
 * Prefix for the tags
 */
const val REFS_TAGS_PREFIX = "refs/tags/"

/**
 * Prefix for the branches
 */
const val REFS_HEADS_PREFIX = "refs/heads/"
