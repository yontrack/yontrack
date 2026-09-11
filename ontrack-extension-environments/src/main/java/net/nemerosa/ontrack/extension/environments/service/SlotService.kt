package net.nemerosa.ontrack.extension.environments.service

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.environments.*
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Project

interface SlotService {

    /**
     * Adding a new slot
     */
    fun addSlot(slot: Slot)

    /**
     * Updating a slot
     */
    fun saveSlot(slot: Slot)

    /**
     * Deleting a slot
     */
    fun deleteSlot(slot: Slot)

    /**
     * Getting a slot using its ID.
     */
    fun getSlotById(id: String): Slot

    /**
     * Gets the list of slots for an environment
     */
    fun findSlotsByEnvironment(environment: Environment): List<Slot>

    /**
     * Adds a configured slot admission rule to a slot
     */
    fun addAdmissionRuleConfig(config: SlotAdmissionRuleConfig)

    /**
     * Saves an existing configured slot admission rule
     */
    fun saveAdmissionRuleConfig(config: SlotAdmissionRuleConfig)

    /**
     * List of configured admission rules for a slot
     */
    fun getAdmissionRuleConfigs(slot: Slot): List<SlotAdmissionRuleConfig>

    /**
     * Deleting a configured admission rule in a slot
     */
    fun deleteAdmissionRuleConfig(config: SlotAdmissionRuleConfig)

    /**
     * Checks if a build is eligible for this slot.
     */
    fun isBuildEligible(slot: Slot, build: Build): Boolean

    /**
     * Gets the last N builds eligible for this slot
     *
     * @param deployable If true, restricts the list of builds to the ones which can actually be deployed.
     */
    fun getEligibleBuilds(
        slot: Slot,
        offset: Int = 0,
        count: Int = 10,
        deployable: Boolean = false,
    ): PaginatedList<Build>

    /**
     * Starting a pipeline
     *
     * @param forceDone If true, creates the pipeline and puts it directly in DONE status
     * @param forceDoneMessage Associated message for the forcing (if null, a default message will be generated)
     * @param skipWorkflows Option to skip the workflows on DONE
     */
    fun startPipeline(
        slot: Slot,
        build: Build,
        forceDone: Boolean = false,
        forceDoneMessage: String? = null,
        skipWorkflows: Boolean = false,
    ): SlotPipeline

    /**
     *
     */
    fun findPipelines(
        slot: Slot,
        offset: Int = 0,
        size: Int = 10,
        buildId: Int? = null,
        branchName: String? = null,
        done: Boolean? = null,
    ): PaginatedList<SlotPipeline>

    /**
     * Cancelling a pipeline
     */
    fun cancelPipeline(pipeline: SlotPipeline, reason: String)

    /**
     * Getting a pipeline by ID
     */
    fun findPipelineById(id: String): SlotPipeline?

    /**
     * Getting the history of a pipeline
     */
    fun getPipelineChanges(pipeline: SlotPipeline): List<SlotPipelineChange>

    /**
     * Checks the progress of being able to run the deployment
     *
     * @param pipelineId ID of the deployment to check
     * @return The progress if possible and null if not at all possible (wrong state)
     */
    fun getDeploymentRunActionProgress(
        pipelineId: String,
    ): SlotPipelineDeploymentStatusProgress?

    /**
     * Starts running a deployment
     *
     * @param force If true, no workflow linked to this deployment is launched
     * and no rule is controlled
     */
    fun runDeployment(
        pipelineId: String,
        dryRun: Boolean = false,
        skipWorkflowId: String? = null,
        force: Boolean = false,
    ): SlotDeploymentActionStatus

    /**
     * Gets the latest (current) pipeline for a slot
     */
    fun getCurrentPipeline(slot: Slot): SlotPipeline?

    /**
     * Checks the progress of being able to finish the deployment
     *
     * @param pipelineId ID of the deployment to check
     * @return The progress if possible and null if not at all possible (wrong state)
     */
    fun getDeploymentFinishActionProgress(
        pipelineId: String,
    ): SlotPipelineDeploymentStatusProgress?

    /**
     * Marking a pipeline as being deployed
     */
    fun finishDeployment(
        pipelineId: String,
        skipWorkflowId: String? = null,
        forcing: Boolean = false,
        message: String? = null,
        skipWorkflows: Boolean = false,
    ): SlotDeploymentActionStatus

    /**
     * Gets the stored states of admission rules for a given pipeline.
     */
    fun getPipelineAdmissionRuleStatuses(pipeline: SlotPipeline): List<SlotPipelineAdmissionRuleStatus>

    /**
     * Get any error message associated with a pipeline.
     *
     * @param pipeline Pipeline to check
     * @return Error message or null if none
     */
    fun getPipelineErrorMessage(pipeline: SlotPipeline): String?

    /**
     * Gets the status (data, override) of a rule for a given pipeline
     */
    fun findPipelineAdmissionRuleStatusByAdmissionRuleConfigId(
        pipeline: SlotPipeline,
        id: String
    ): SlotPipelineAdmissionRuleStatus?

    /**
     * Getting a check on a rule for a pipeline
     */
    fun getAdmissionRuleCheck(
        ruleStatus: SlotPipelineAdmissionRuleStatus,
    ): SlotDeploymentCheck

    /**
     * Given a pipeline and a rule, returns its status
     */
    fun getAdmissionRuleCheck(
        pipeline: SlotPipeline,
        admissionRule: SlotAdmissionRuleConfig
    ): SlotDeploymentCheck

    /**
     * Overriding an admission rule
     */
    fun overrideAdmissionRule(pipeline: SlotPipeline, admissionRuleConfig: SlotAdmissionRuleConfig, message: String)

    /**
     * Setting up some data for a rule in a pipeline
     */
    fun setupAdmissionRule(
        pipeline: SlotPipeline,
        admissionRuleConfig: SlotAdmissionRuleConfig,
        data: JsonNode,
    )

    /**
     * Finds a configured admission rule using its ID
     */
    fun findAdmissionRuleConfigById(id: String): SlotAdmissionRuleConfig?

    /**
     * Given one build, per qualifier, gets the highest deployed slot. If a build is deployed
     * more than once (for several qualifiers), several pipelines are returned.
     */
    fun findHighestDeployedSlotPipelinesByBuildAndQualifier(build: Build): Set<SlotPipeline>

    /**
     * Gets the list of currently deployed pipelines for this build.
     *
     * @param build Build to get the current deployments for
     * @param qualifier If not null, keeps only the slots having this exact qualifier. Null - the
     *                  default - means *any* qualifier, in line with [findSlotsByProject]. Note that
     *                  the empty string is a qualifier like any other (the default one) and is
     *                  therefore a filter, not a wildcard.
     * @return List of deployments, sorted by decreasing environment order
     */
    fun findCurrentDeployments(build: Build, qualifier: String? = null): List<SlotPipeline>

    /**
     * Finds all the slots for the given project and optional qualifier.
     *
     * @param project Project to get the slots for
     * @param qualifier If not null, additional filter on the qualifier
     * @return List of slots
     */
    fun findSlotsByProject(project: Project, qualifier: String? = null): Set<Slot>

    /**
     * Finds all the slots for the given project, qualifier and environment.
     *
     * @param environment Environment
     * @param project Project to get the slots for
     * @param qualifier Qualifier
     * @return List of slots
     */
    fun findSlotByProjectAndEnvironment(environment: Environment, project: Project, qualifier: String): Slot?

    /**
     * Finds the last pipeline of this slot marked as [SlotPipelineStatus.DONE].
     *
     * @param slot Slot where to find the pipeline
     * @return Last deployed pipeline or `null` if none is present
     */
    fun getLastDeployedPipeline(slot: Slot): SlotPipeline?

    /**
     * Gets a list of slots accessible to the given build (having the same project)
     * and their eligibility status.
     */
    fun getEligibleSlotsForBuild(build: Build): List<EligibleSlot>

    /**
     * Given a pipeline, returns its list of required inputs.
     */
    fun getRequiredInputs(pipeline: SlotPipeline): List<SlotAdmissionRuleInput>

    /**
     * Gets all the pipelines associated with a build.
     */
    fun findPipelineByBuild(build: Build): List<SlotPipeline>

    /**
     * Gets all the slots eligible for the build.
     */
    fun findEligibleSlotsByBuild(build: Build): List<Slot>

    /**
     * Gets all the slot pipelines where the given [build] is the last being deployed.
     */
    fun findSlotPipelinesWhereBuildIsLastDeployed(build: Build): List<SlotPipeline>

    /**
     * Deletes a deployment using its ID.
     */
    fun deleteDeployment(id: String)

}