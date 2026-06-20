package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.common.Document
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.pagination.PaginatedList
import org.springframework.security.access.AccessDeniedException
import java.time.LocalDateTime
import java.util.*
import java.util.function.BiFunction

/**
 * Not deprecated yet, but please don't create any new method in this service, prefer more
 * granular services.
 */
interface StructureService {

    val projectList: List<Project>

    // Projects

    fun newProject(project: Project): Project

    /**
     * Looks for a project using its ID.
     * @param projectId ID of the project
     * @return Project or `null` if not found
     */
    fun findProjectByID(projectId: ID): Project?

    /**
     * Finds a list of projects using part of their name
     *
     * @param pattern Part to look for, case-insensitive
     * @return List of projects
     */
    fun findProjectsByNamePattern(pattern: String): List<Project>

    fun getProject(projectId: ID): Project

    fun saveProject(project: Project)

    fun disableProject(project: Project): Project

    fun enableProject(project: Project): Project

    fun deleteProject(projectId: ID): Ack

    // Branches

    /**
     * Looks for a branch using its ID.
     * @param branchId ID of the branch
     * @return Branch or `null` if not found
     */
    fun findBranchByID(branchId: ID): Branch?

    fun getBranch(branchId: ID): Branch

    fun getBranchesForProject(projectId: ID): List<Branch>

    fun filterBranchesForProject(project: Project, filter: BranchFilter): List<Branch>

    fun newBranch(branch: Branch): Branch

    fun saveBranch(branch: Branch)

    fun disableBranch(branch: Branch): Branch

    fun enableBranch(branch: Branch): Branch

    fun deleteBranch(branchId: ID): Ack

    // Builds

    fun newBuild(build: Build): Build

    fun saveBuild(build: Build): Build

    /**
     * Looks for a build using its ID.
     * @param buildId ID of the build
     * @return Build or `null` if not found
     */
    fun findBuildByID(buildId: ID): Build?

    fun getBuild(buildId: ID): Build

    // TODO Replace by Build?
    fun findBuildByName(project: String, branch: String, build: String): Optional<Build>

    /**
     * Finds a build on a branch whose name is the closest. It assumes that build names
     * are in a numeric format.
     */
    // TODO Replace by Build?
    fun findBuildAfterUsingNumericForm(id: ID, buildName: String): Optional<Build>

    fun getLastBuildForBranch(branch: Branch): Build?

    /**
     * Gets the number of builds for a branch.
     */
    fun getBuildCount(branch: Branch): Int

    /**
     * Gets the number of builds for a project.
     *
     * @param project Project to get the build count for
     * @return Number of builds in this project
     */
    fun getBuildCountForProject(project: Project): Int

    fun deleteBuild(buildId: ID): Ack

    fun getPreviousBuild(buildId: ID): Build?

    fun getNextBuild(buildId: ID): Build?

    /**
     * Creates a qualified build link
     */
    fun createBuildLink(fromBuild: Build, toBuild: Build, qualifier: String = BuildLink.DEFAULT)

    /**
     * Deletes a qualified build link
     */
    fun deleteBuildLink(fromBuild: Build, toBuild: Build, qualifier: String)

    /**
     * Gets the total number of downstream links
     *
     * @param build  Source build
     * @return Number of downstream links
     */
    fun getCountQualifiedBuildsUsedBy(
        build: Build,
    ): Int

    /**
     * Gets the builds used by the given one.
     *
     * @param build  Source build
     * @param offset Offset for pagination
     * @param size   Page size for pagination
     * @param depth  If greater than 0, looks for children up to this depth
     * @param filter Optional filter on the build link
     * @return List of qualified build links which are used by the given one
     */
    fun getQualifiedBuildsUsedBy(
        build: Build,
        offset: Int = 0,
        size: Int = 10,
        depth: Int = 0,
        filter: (link: BuildLink) -> Boolean = { true },
    ): PaginatedList<BuildLink>

    /**
     * Gets the builds which use the given one.
     *
     * @param build  Source build
     * @param offset Offset for pagination
     * @param size   Page size for pagination
     * @param filter Optional filter on the build links
     * @return List of builds which use the given one
     */
    fun getQualifiedBuildsUsing(build: Build, offset: Int = 0, size: Int = 10, filter: (BuildLink) -> Boolean = { true }): PaginatedList<BuildLink>

    fun editBuildLinks(build: Build, form: BuildLinkForm)

    fun isLinkedFrom(build: Build, project: String, buildPattern: String? = null, qualifier: String? = null): Boolean

    fun isLinkedTo(build: Build, project: String, buildPattern: String? = null, qualifier: String? = null): Boolean
    fun isLinkedTo(build: Build, targetBuild: Build, qualifier: String? = null): Boolean

    /**
     * Loops over ALL the build links. Use this method with care, mostly for external indexation.
     */
    fun forEachBuildLink(code: (from: Build, to: Build, qualifier: String) -> Unit)

    /**
     * Looks for the first build which matches a given predicate.
     *
     * @param branchId       Branch to look builds into
     * @param sortDirection  Build search direction
     * @param buildPredicate Predicate for a match
     * @return Build if found, `null` otherwise
     */
    fun findBuild(branchId: ID, sortDirection: BuildSortDirection, buildPredicate: (Build) -> Boolean): Build?

    /**
     * Loops over all the builds of a branch, stopping when [processor] returns `false`.
     *
     * @param branch Branch to look builds into
     * @param processor Must return `false` when the looping must stop
     * @param sortDirection  Build search direction
     */
    fun forEachBuild(branch: Branch, sortDirection: BuildSortDirection, processor: (Build) -> Boolean)

    // TODO Replace by Build?
    fun getLastBuild(branchId: ID): Optional<Build>

    fun buildSearch(projectId: ID, form: BuildSearchForm): List<Build>

    // Promotion levels

    fun getPromotionLevelListForBranch(branchId: ID): List<PromotionLevel>

    fun newPromotionLevel(promotionLevel: PromotionLevel): PromotionLevel

    fun getPromotionLevel(promotionLevelId: ID): PromotionLevel

    /**
     * Looks for a promotion level using its ID.
     * @param promotionLevelId ID of the promotion level
     * @return Promotion level or `null` if not found
     */
    fun findPromotionLevelByID(promotionLevelId: ID): PromotionLevel?

    fun getPromotionLevelImage(promotionLevelId: ID): Document

    fun setPromotionLevelImage(promotionLevelId: ID, document: Document?)

    fun savePromotionLevel(promotionLevel: PromotionLevel)

    fun deletePromotionLevel(promotionLevelId: ID): Ack

    fun reorderPromotionLevels(branchId: ID, reordering: Reordering)

    fun setPromotionLevelFields(promotionLevelId: ID, fields: List<PromotionLevelField>)

    fun newPromotionLevelFromPredefined(branch: Branch, predefinedPromotionLevel: PredefinedPromotionLevel): PromotionLevel

    fun getOrCreatePromotionLevel(branch: Branch, promotionLevelId: Int?, promotionLevelName: String?): PromotionLevel

    // Promotion runs

    fun newPromotionRun(promotionRun: PromotionRun): PromotionRun

    fun getPromotionRun(promotionRunId: ID): PromotionRun

    /**
     * Looks for a promotion run using its ID.
     * @param promotionRunId ID of the promotion run
     * @return Promotion run or `null` if not found
     */
    fun findPromotionRunByID(promotionRunId: ID): PromotionRun?

    // TODO Replace by PromotionLevel?
    fun findPromotionLevelByName(project: String, branch: String, promotionLevel: String): Optional<PromotionLevel>

    fun getPromotionRunsForBuild(buildId: ID): List<PromotionRun>

    fun getLastPromotionRunsForBuild(buildId: ID): List<PromotionRun>

    /**
     * Optimized version of getting the last promotion runs for a build, by passing preloaded (or cached)
     * list of the promotion levels for the branch and the build itself.
     */
    fun getLastPromotionRunsForBuild(build: Build, promotionLevels: List<PromotionLevel>): List<PromotionRun>

    // TODO Replace by PromotionRun?
    fun getLastPromotionRunForBuildAndPromotionLevel(build: Build, promotionLevel: PromotionLevel): Optional<PromotionRun>

    fun getPromotionRunsForBuildAndPromotionLevel(build: Build, promotionLevel: PromotionLevel): List<PromotionRun>

    fun getLastPromotionRunForPromotionLevel(promotionLevel: PromotionLevel): PromotionRun?

    fun deletePromotionRun(promotionRunId: ID): Ack

    // TODO Replace by PromotionRun?
    fun getEarliestPromotionRunAfterBuild(promotionLevel: PromotionLevel, build: Build): Optional<PromotionRun>

    fun getPromotionRunsForPromotionLevel(promotionLevelId: ID): List<PromotionRun>

    /**
     * Bulk update of all promotion levels in other projects/branches and in predefined promotion levels,
     * following the model designed by the promotion level ID.
     *
     * @param promotionLevelId ID of the promotion level model
     * @return Result of the update
     */
    fun bulkUpdatePromotionLevels(promotionLevelId: ID): Ack

    // Validation stamps

    fun getValidationStampListForBranch(branchId: ID): List<ValidationStamp>

    fun newValidationStamp(validationStamp: ValidationStamp): ValidationStamp

    fun getValidationStamp(validationStampId: ID): ValidationStamp

    /**
     * Looks for a validation stamp using its ID.
     * @param validationStampId ID of the validation stamp
     * @return Validation stamp or `null` if not found
     */
    fun findValidationStampByID(validationStampId: ID): ValidationStamp?

    // TODO Replace by ValidationStamp?
    fun findValidationStampByName(project: String, branch: String, validationStamp: String): Optional<ValidationStamp>

    fun getValidationStampImage(validationStampId: ID): Document

    fun setValidationStampImage(validationStampId: ID, document: Document?)

    fun saveValidationStamp(validationStamp: ValidationStamp)

    fun deleteValidationStamp(validationStampId: ID): Ack

    fun reorderValidationStamps(branchId: ID, reordering: Reordering)

    fun newValidationStampFromPredefined(branch: Branch, stamp: PredefinedValidationStamp): ValidationStamp

    fun getOrCreateValidationStamp(branch: Branch, validationStampName: String): ValidationStamp

    /**
     * Bulk update of all validation stamps in other projects/branches and in predefined validation stamps,
     * following the model designed by the validation stamp ID.
     *
     * @param validationStampId ID of the validation stamp model
     * @return Result of the update
     */
    fun bulkUpdateValidationStamps(validationStampId: ID): Ack

    // Validation runs

    fun newValidationRun(build: Build, validationRunRequest: ValidationRunRequest): ValidationRun

    /**
     * Deletes an existing validation run
     */
    fun deleteValidationRun(validationRun: ValidationRun): Ack

    fun getValidationRun(validationRunId: ID): ValidationRun

    /**
     * Looks for a validation run using its ID.
     * @param validationRunId ID of the validation run
     * @return Validation run or `null` if not found
     */
    fun findValidationRunByID(validationRunId: ID): ValidationRun?

    /**
     * Gets the list of validation runs for a build.
     *
     * @param buildId      ID of the build
     * @param offset       Offset in the list
     * @param count        Maximum number of elements to return
     * @param sortingMode  How to sort the runs ([ValidationRunSortingMode.ID] by default)
     * @param statuses List of statuses to filter upon
     * @return List of validation runs
     */
    fun getValidationRunsForBuild(
        buildId: ID,
        offset: Int,
        count: Int,
        sortingMode: ValidationRunSortingMode = ValidationRunSortingMode.ID,
        statuses: List<String>? = null,
    ): List<ValidationRun>

    /**
     * Gets the number of validation runs for a build.
     *
     * @param buildId ID of the build
     * @param statuses List of statuses to filter upon
     * @return Number of validation runs
     */
    fun getValidationRunsCountForBuild(buildId: ID, statuses: List<String>? = null): Int

    /**
     * Gets the list of validation runs for a build and a validation stamp.
     *
     * @param buildId           ID of the build
     * @param validationStampId ID of the validation stamp
     * @param offset            Offset in the list
     * @param count             Maximum number of elemnts to return
     * @return List of validation runs
     */
    fun getValidationRunsForBuildAndValidationStamp(
        buildId: ID,
        validationStampId: ID,
        offset: Int,
        count: Int,
        sortingMode: ValidationRunSortingMode? = ValidationRunSortingMode.ID,
        statuses: List<String>? = null,
    ): List<ValidationRun>

    /**
     * Gets the list of validation runs for a build and a validation stamp.
     *
     * @param build           Build
     * @param validationStamp Validation stamp
     * @param offset            Offset in the list
     * @param count             Maximum number of elemnts to return
     * @return List of validation runs
     */
    fun getValidationRunsForBuildAndValidationStamp(
        build: Build,
        validationStamp: ValidationStamp,
        offset: Int = 0,
        count: Int = 10,
        sortingMode: ValidationRunSortingMode? = ValidationRunSortingMode.ID,
        statuses: List<String>? = null,
    ): List<ValidationRun>

    /**
     * Gets the list of validation runs for a build and a validation stamp, and a list of accepted statuses
     *
     * @param buildId           ID of the build
     * @param validationStampId ID of the validation stamp
     * @param statuses          List of statuses for the last status of the run
     * @param offset            Offset in the list
     * @param count             Maximum number of elemnts to return
     * @return List of validation runs
     */
    fun getValidationRunsForBuildAndValidationStampAndStatus(
            buildId: ID,
            validationStampId: ID,
            statuses: List<ValidationRunStatusID>,
            offset: Int,
            count: Int
    ): List<ValidationRun>

    fun getValidationRunsForValidationStamp(validationStamp: ValidationStamp, offset: Int, count: Int): List<ValidationRun>

    /**
     * Gets the validation runs for a given validation stamp between two timestamps.
     */
    fun getValidationRunsForValidationStampBetweenDates(validationStampId: ID, start: LocalDateTime, end: LocalDateTime): List<ValidationRun>

    fun getValidationRunsForValidationStamp(validationStampId: ID, offset: Int, count: Int): List<ValidationRun>

    /**
     * Gets the list of validation runs for a given validation stamp and a list of statuses.
     * @param validationStamp   Validation stamp
     * @param statuses          List of statuses for the last status of the run
     * @param offset            Offset in the list
     * @param count             Maximum number of elemnts to return
     * @return List of validation runs
     */
    fun getValidationRunsForValidationStampAndStatus(
            validationStamp: ValidationStamp,
            statuses: List<ValidationRunStatusID>,
            offset: Int,
            count: Int
    ): List<ValidationRun>

    /**
     * Gets the list of validation runs for a given validation stamp and a list of statuses.
     * @param validationStampId ID of the validation stamp
     * @param statuses          List of statuses for the last status of the run
     * @param offset            Offset in the list
     * @param count             Maximum number of elemnts to return
     * @return List of validation runs
     */
    fun getValidationRunsForValidationStampAndStatus(
            validationStampId: ID,
            statuses: List<ValidationRunStatusID>,
            offset: Int,
            count: Int
    ): List<ValidationRun>

    /**
     * Gets the list of validation runs for a branch and a list of statuses.
     * @param branchId ID of the branch
     * @param statuses          List of statuses for the last status of the run
     * @param offset            Offset in the list
     * @param count             Maximum number of elemnts to return
     * @return List of validation runs
     */
    fun getValidationRunsForStatus(
            branchId: ID,
            statuses: List<ValidationRunStatusID>,
            offset: Int,
            count: Int
    ): List<ValidationRun>

    fun newValidationRunStatus(validationRun: ValidationRun, runStatus: ValidationRunStatus): ValidationRun

    /**
     * Re-exporting all validation run data metrics.
     */
    fun restoreValidationRunDataMetrics(logger: (String) -> Unit = {})

    /**
     * Gets the parent validation run for a given validation run status ID
     *
     * @param validationRunStatusId ID of the validation run status ID
     * @param checkForAccess `false` if the access for view must be checked
     * @return Validation run which contains the status ID or `null` if the current user
     * has no access to it. Never `null` if the [checkForAccess] if `true`.
     */
    fun getParentValidationRun(validationRunStatusId: ID, checkForAccess: Boolean = true): ValidationRun?

    /**
     * Gets a validation run status using its ID.
     *
     * @param id ID of the validation run status
     * @return Validation run status
     */
    fun getValidationRunStatus(id: ID): ValidationRunStatus

    /**
     * Edits a validation run status comment.
     *
     * @param run Parent validation run
     * @param runStatusId ID of the specific run status to edit
     * @param comment New comment
     * @return Updated validation run
     */
    fun saveValidationRunStatusComment(run: ValidationRun, runStatusId: ID, comment: String): ValidationRun

    /**
     * Checks if the validation run status comment is editable by the current user
     */
    fun isValidationRunStatusCommentEditable(validationRunStatus: ID): Boolean

    /**
     * Gets the total number of validation runs for a build and a validation stamp
     *
     * @param buildId           ID of the build
     * @param validationStampId ID of the validation stamp
     * @return Number of validation runs for the validation stamp
     */
    fun getValidationRunsCountForBuildAndValidationStamp(
        buildId: ID,
        validationStampId: ID,
        statuses: List<String>? = null,
    ): Int

    /**
     * Gets the total number of validation runs for a validation stamp
     *
     * @param validationStampId ID of the validation stamp
     * @return Number of validation runs for the validation stamp
     */
    fun getValidationRunsCountForValidationStamp(validationStampId: ID): Int

    // Entity searches by name

    // TODO Replace by Project?
    fun findProjectByName(project: String): Optional<Project>

    /**
     * Gets the project by its name if authorized to access it. If it does exist, but the user is
     * not authorized to see it, throws an [AccessDeniedException]
     *
     * @param project Name of the project to look for
     * @return Project if it exists and is authorized, or null if if does not exist
     * @throws AccessDeniedException If the project does exist but the user has no access to it
     */
    fun findProjectByNameIfAuthorized(project: String): Project?

    // TODO Replace by Branch?
    fun findBranchByName(project: String, branch: String): Optional<Branch>

    fun entityLoader(): BiFunction<ProjectEntityType, ID, ProjectEntity>

    /**
     * Gets the list of the most active projects
     *
     * @param count Maximum number of projects to return
     */
    fun lastActiveProjects(count: Int): List<Project>

}
