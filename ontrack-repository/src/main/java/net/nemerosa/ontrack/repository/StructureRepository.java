package net.nemerosa.ontrack.repository;

import net.nemerosa.ontrack.common.Document;
import net.nemerosa.ontrack.model.Ack;
import net.nemerosa.ontrack.model.structure.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @deprecated Use dedicated repositories at entity level
 */
public interface StructureRepository {

    // Projects

    Project newProject(Project project);

    List<Project> getProjectList();

    /**
     * Looks for a project using its ID.
     *
     * @param projectId ID of the project
     * @return Project or <code>null</code> if not found
     */
    @Nullable
    Project findProjectByID(ID projectId);

    /**
     * Finds a list of projects using part of their name
     *
     * @param pattern Part to look for, case-insensitive
     * @return List of projects
     */
    @NotNull
    List<Project> findProjectsByNamePattern(@NotNull String pattern);

    /**
     * Finds the projects matching a name pattern and carrying all the given labels, in one query.
     *
     * <p>Both criteria are combined with AND, and so are the labels between themselves: a project
     * is returned only when it carries every one of them. Omitting a criterion (a blank pattern, an
     * empty list of labels) drops it, so that no criterion at all returns the whole list.
     *
     * @param namePattern Part of the name to look for, case-insensitive, or <code>null</code>
     * @param labels      Labels the project must all carry, as <code>category:name</code> display
     *                    strings (just <code>name</code> for a label without a category)
     * @return List of projects, ordered by name
     */
    @NotNull
    List<Project> findProjects(@Nullable String namePattern, @NotNull List<String> labels);

    @NotNull
    Project getProject(ID projectId);

    Optional<Project> getProjectByName(String project);

    void saveProject(Project project);

    Ack deleteProject(ID projectId);

    // Branches

    /**
     * Looks for a branch using its ID.
     *
     * @param branchId ID of the project
     * @return Branch or `null` if not found
     */
    @Nullable
    Branch findBranchByID(ID branchId);

    @NotNull
    Branch getBranch(ID branchId);

    Optional<Branch> getBranchByName(String project, String branch);

    List<Branch> getBranchesForProject(ID projectId);

    Branch newBranch(Branch branch);

    void saveBranch(Branch branch);

    Ack deleteBranch(ID branchId);

    // Builds

    Build newBuild(Build build);

    Build saveBuild(Build build);

    /**
     * Looks for a build using its ID.
     *
     * @param buildId ID of the build
     * @return Build or `null` if not found
     */
    @Nullable
    Build findBuildByID(ID buildId);

    @NotNull
    Build getBuild(ID buildId);

    Optional<Build> getBuildByName(String project, String branch, String build);

    Optional<Build> findBuildAfterUsingNumericForm(ID branchId, String buildName);

    int getBuildCount(Branch branch);

    /**
     * Gets the number of builds of the branch of a build which are more recent than this build.
     *
     * @param build Build to count from
     * @return Number of builds after this one on its own branch, 0 when it is the branch's latest
     */
    int getNewerBuildCount(Build build);

    /**
     * Gets the number of builds for a project.
     *
     * @param project Project to get the build count for
     * @return Number of builds in this project
     */
    int getBuildCountForProject(Project project);

    @Nullable
    Build getPreviousBuild(Build build);

    @Nullable
    Build getNextBuild(Build build);

    /**
     * Iterates over the builds of the branch, from the newest to the oldest, until
     * the <code>buildPredicate</code> returns <code>false</code>.
     */
    default void builds(Branch branch, Predicate<Build> buildPredicate) {
        builds(branch, buildPredicate, BuildSortDirection.FROM_NEWEST);
    }

    /**
     * Iterates over the builds of the branch.
     */
    void builds(Branch branch, Predicate<Build> buildPredicate, BuildSortDirection sortDirection);

    /**
     * Iterates over the builds of the project, from the newest to the oldest, until
     * the <code>buildPredicate</code> returns <code>false</code>.
     */
    void builds(Project project, Predicate<Build> buildPredicate);

    Build getLastBuildForBranch(Branch branch);

    Ack deleteBuild(ID buildId);

    /**
     * Gets the builds used by the given one.
     *
     * @param build Source build
     * @return List of builds used by the given one
     * @deprecated Use {@link BuildLinkRepository#getQualifiedBuildsUsedBy(Build)} instead.
     */
    List<Build> getBuildsUsedBy(Build build);

    /**
     * Gets the builds which use the given one.
     *
     * @param build Source build
     * @return List of builds which use the given one
     * @deprecated Use {@link BuildLinkRepository#getQualifiedBuildsUsing(Build)} instead.
     */
    List<Build> getBuildsUsing(Build build);

    // Promotion levels

    List<PromotionLevel> getPromotionLevelListForBranch(ID branchId);

    PromotionLevel newPromotionLevel(PromotionLevel promotionLevel);

    PromotionLevel getPromotionLevel(ID promotionLevelId);

    /**
     * Looks for a promotion level using its ID.
     *
     * @param promotionLevelId ID of the promotion level
     * @return Promotion level or `null` if not found
     */
    @Nullable
    PromotionLevel findPromotionLevelByID(ID promotionLevelId);

    Optional<PromotionLevel> getPromotionLevelByName(String project, String branch, String promotionLevel);

    Optional<PromotionLevel> getPromotionLevelByName(Branch branch, String promotionLevel);

    Document getPromotionLevelImage(ID promotionLevelId);

    void setPromotionLevelImage(ID promotionLevelId, Document document);

    void savePromotionLevel(PromotionLevel promotionLevel);

    Ack deletePromotionLevel(ID promotionLevelId);

    void reorderPromotionLevels(ID branchId, Reordering reordering);

    // Promotion runs

    PromotionRun newPromotionRun(PromotionRun promotionRun);

    PromotionRun getPromotionRun(ID promotionRunId);

    /**
     * Looks for a promotion run using its ID.
     *
     * @param promotionRunId ID of the promotion run
     * @return Promotion run or `null` if not found
     */
    @Nullable
    PromotionRun findPromotionRunByID(ID promotionRunId);

    Ack deletePromotionRun(ID promotionRunId);

    List<PromotionRun> getPromotionRunsForBuild(Build build);

    List<PromotionRun> getLastPromotionRunsForBuild(Build build);

    /**
     * Optimized version of [getLastPromotionRunsForBuild] with cached list of
     * promotion levels
     */
    List<PromotionRun> getLastPromotionRunsForBuild(Build build, List<PromotionLevel> promotionLevels);

    PromotionRun getLastPromotionRunForPromotionLevel(PromotionLevel promotionLevel);

    Optional<PromotionRun> getLastPromotionRun(Build build, PromotionLevel promotionLevel);

    List<PromotionRun> getPromotionRunsForBuildAndPromotionLevel(Build build, PromotionLevel promotionLevel);

    List<PromotionRun> getPromotionRunsForPromotionLevel(PromotionLevel promotionLevel);

    Optional<PromotionRun> getEarliestPromotionRunAfterBuild(PromotionLevel promotionLevel, Build build);

    /**
     * Updates all promotion levels having the same name than the model, on all branches. The description
     * and the image are copied from the model.
     *
     * @param promotionLevelId ID of the model
     */
    void bulkUpdatePromotionLevels(ID promotionLevelId);

    // Validation stamps

    List<ValidationStamp> getValidationStampListForBranch(ID branchId);

    ValidationStamp newValidationStamp(ValidationStamp validationStamp);

    ValidationStamp getValidationStamp(ID validationStampId);

    /**
     * Looks for a validation stamp using its ID.
     *
     * @param validationStampId ID of the validation stamp
     * @return Validation stamp or `null` if not found
     */
    @Nullable
    ValidationStamp findValidationStampByID(ID validationStampId);

    Optional<ValidationStamp> getValidationStampByName(String project, String branch, String validationStamp);

    Optional<ValidationStamp> getValidationStampByName(Branch branch, String validationStamp);

    Document getValidationStampImage(ID validationStampId);

    void setValidationStampImage(ID validationStampId, Document document);

    void bulkUpdateValidationStamps(ID validationStampId);

    void saveValidationStamp(ValidationStamp validationStamp);

    Ack deleteValidationStamp(ID validationStampId);

    Ack deleteValidationRun(ID validationRunId);

    void reorderValidationStamps(ID branchId, Reordering reordering);

    // Validation runs

    /**
     * Looping over ALL validation runs.
     *
     * @param validationRunStatusService Run status mapping function (provided by caller)
     * @param processing                 Processing code
     */
    void forEachValidationRun(Function<String, ValidationRunStatusID> validationRunStatusService, Consumer<ValidationRun> processing);

    ValidationRun newValidationRun(ValidationRun validationRun, Function<String, ValidationRunStatusID> validationRunStatusService);

    ValidationRun getValidationRun(ID validationRunId, Function<String, ValidationRunStatusID> validationRunStatusService);

    /**
     * Looks for a validation run using its ID.
     *
     * @param validationRunId ID of the validation run
     * @return Validation run or `null` if not found
     */
    @Nullable
    ValidationRun findValidationRunByID(ID validationRunId, Function<String, ValidationRunStatusID> validationRunStatusService);

    /**
     * Gets the list of validation runs for a build.
     *
     * @param build                      Build to get the validation runs for
     * @param offset                     Offset in the list
     * @param count                      Maximum number of elements to return
     * @param validationRunStatusService Run status mapping function (provided by caller)
     * @param sortingMode                How to sort the runs (null is {@link ValidationRunSortingMode#ID} by default)
     * @return List of validation runs
     */
    List<ValidationRun> getValidationRunsForBuild(Build build, int offset, int count,
                                                  @Nullable ValidationRunSortingMode sortingMode,
                                                  @Nullable List<String> statuses,
                                                  Function<String, ValidationRunStatusID> validationRunStatusService);

    /**
     * Gets the number of validation runs for a build.
     *
     * @param build Build to get the validation runs for
     * @return Number of validation runs
     */
    int getValidationRunsCountForBuild(Build build, @Nullable List<String> statuses);

    List<ValidationRun> getValidationRunsForBuildAndValidationStamp(
            Build build,
            ValidationStamp validationStamp,
            int offset,
            int count,
            @Nullable ValidationRunSortingMode sortingMode,
            @Nullable List<String> statuses,
            Function<String, ValidationRunStatusID> validationRunStatusService
    );

    List<ValidationRun> getValidationRunsForBuildAndValidationStampAndStatus(Build build, ValidationStamp validationStamp, List<ValidationRunStatusID> statuses, int offset, int count, Function<String, ValidationRunStatusID> validationRunStatusService);

    List<ValidationRun> getValidationRunsForValidationStamp(ValidationStamp validationStamp, int offset, int count, Function<String, ValidationRunStatusID> validationRunStatusService);


    /**
     * Gets the list of validation runs for a validation stamp between two timestamps.
     */
    @NotNull
    List<ValidationRun> getValidationRunsForValidationStampBetweenDates(@NotNull ValidationStamp validationStamp,
                                                                        @NotNull LocalDateTime start,
                                                                        @NotNull LocalDateTime end,
                                                                        @NotNull Function<String, ValidationRunStatusID> validationRunStatusService);

    List<ValidationRun> getValidationRunsForValidationStampAndStatus(ValidationStamp validationStamp, List<ValidationRunStatusID> statuses, int offset, int count, Function<String, ValidationRunStatusID> validationRunStatusService);

    List<ValidationRun> getValidationRunsForStatus(Branch branch, List<ValidationRunStatusID> statuses, int offset, int count, Function<String, ValidationRunStatusID> validationRunStatusService);

    /**
     * Gets the total number of validation runs for a build and a validation stamp
     *
     * @param buildId           ID of the build
     * @param validationStampId ID of the validation stamp
     * @return Number of validation runs for the validation stamp
     */
    int getValidationRunsCountForBuildAndValidationStamp(
            ID buildId,
            ID validationStampId,
            @Nullable List<String> statuses
    );

    /**
     * Gets the total number of validation runs for a validation stamp
     *
     * @param validationStampId ID of the validation stamp
     * @return Number of validation runs for the validation stamp
     */
    int getValidationRunsCountForValidationStamp(ID validationStampId);

    ValidationRun newValidationRunStatus(ValidationRun validationRun, ValidationRunStatus runStatus);


    /**
     * Gets the parent validation run for a given validation run status ID
     */
    @NotNull
    ValidationRun getParentValidationRun(@NotNull ID validationRunStatusId, Function<String, ValidationRunStatusID> validationRunStatusService);

    /**
     * Loads a validation run status
     */
    @Nullable
    ValidationRunStatus getValidationRunStatus(ID id, Function<String, ValidationRunStatusID> validationRunStatusService);

    /**
     * Saves a comment for a run status
     */
    void saveValidationRunStatusComment(@NotNull ValidationRunStatus runStatus, @NotNull String comment);
}
