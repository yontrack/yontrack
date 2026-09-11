package net.nemerosa.ontrack.extension.environments.service

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.environments.*
import net.nemerosa.ontrack.extension.environments.events.EnvironmentsEventsFactory
import net.nemerosa.ontrack.extension.environments.rules.SlotAdmissionRuleRegistry
import net.nemerosa.ontrack.extension.environments.security.*
import net.nemerosa.ontrack.extension.environments.storage.*
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.security.ProjectView
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Project
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class SlotServiceImpl(
    private val securityService: SecurityService,
    private val slotRepository: SlotRepository,
    private val slotAdmissionRuleConfigRepository: SlotAdmissionRuleConfigRepository,
    private val slotPipelineRepository: SlotPipelineRepository,
    private val slotPipelineChangeRepository: SlotPipelineChangeRepository,
    private val slotPipelineAdmissionRuleStatusRepository: SlotPipelineAdmissionRuleStatusRepository,
    private val slotAdmissionRuleRegistry: SlotAdmissionRuleRegistry,
    private val slotWorkflowService: SlotWorkflowService,
    private val eventPostService: EventPostService,
    private val environmentsEventsFactory: EnvironmentsEventsFactory,
) : SlotService {

    override fun addSlot(slot: Slot) {
        securityService.checkSlotAccess<SlotCreate>(slot)
        // Checks for ID
        if (slotRepository.findSlotById(slot.id) != null) {
            throw SlotIdAlreadyExistsException(slot.id)
        }
        // Checks for project & qualifier
        val existing =
            slotRepository.findByEnvironmentAndProjectAndQualifier(slot.environment, slot.project, slot.qualifier)
        if (existing != null) {
            throw SlotAlreadyDefinedException(slot.environment, slot.project, slot.qualifier)
        }
        // Saving
        slotRepository.addSlot(slot)
        eventPostService.post(environmentsEventsFactory.slotCreation(slot))
    }

    override fun saveSlot(slot: Slot) {
        securityService.checkSlotAccess<SlotUpdate>(slot)
        slotRepository.saveSlot(slot)
    }

    override fun findSlotsByEnvironment(environment: Environment): List<Slot> =
        if (securityService.isGlobalFunctionGranted(EnvironmentList::class.java)) {
            slotRepository.findByEnvironment(environment)
                .filter {
                    securityService.isProjectFunctionGranted(it.project, ProjectView::class.java)
                }
                .sortedBy { it.project.name }
        } else {
            emptyList()
        }

    override fun addAdmissionRuleConfig(config: SlotAdmissionRuleConfig) {
        securityService.checkSlotAccess<SlotUpdate>(config.slot)
        // Controls the name
        config.checkName()
        // TODO Controls the provided configuration
        slotAdmissionRuleConfigRepository.addAdmissionRuleConfig(config)
        eventPostService.post(environmentsEventsFactory.slotUpdated(config.slot))
    }

    override fun getRequiredInputs(pipeline: SlotPipeline): List<SlotAdmissionRuleInput> =
        if (pipeline.status == SlotPipelineStatus.CANDIDATE) {
            val rules = getAdmissionRuleConfigs(pipeline.slot)
            rules.mapNotNull { config ->
                val rule = slotAdmissionRuleRegistry.getRule(config.ruleId)
                getRequiredInput(pipeline, config, rule)
            }
        } else {
            emptyList()
        }

    override fun findPipelineByBuild(build: Build): List<SlotPipeline> =
        slotPipelineRepository.findPipelineByBuild(build)
            .filter { securityService.isSlotAccessible<ProjectView>(it.slot) }

    override fun findEligibleSlotsByBuild(build: Build): List<Slot> {
        val projectSlots = findSlotsByProject(build.project)
        return projectSlots.filter {
            isBuildEligible(it, build)
        }.sortedBy { it.environment.order }
    }

    override fun findSlotPipelinesWhereBuildIsLastDeployed(build: Build): List<SlotPipeline> {
        val projectSlots = findSlotsByProject(build.project)
        return projectSlots.mapNotNull { slot ->
            getCurrentPipeline(slot)
        }.filter { pipeline ->
            pipeline.build.id == build.id
        }.sortedByDescending { pipeline ->
            pipeline.slot.environment.order
        }
    }

    override fun deleteDeployment(id: String) {
        val deployment = findPipelineById(id)
        if (deployment != null) {
            securityService.checkSlotAccess<SlotPipelineDelete>(deployment.slot)
            slotPipelineRepository.deleteDeployment(id)
        }
    }

    private fun <C : Any, D> getRequiredInput(
        pipeline: SlotPipeline,
        config: SlotAdmissionRuleConfig,
        rule: SlotAdmissionRule<C, D>
    ): SlotAdmissionRuleInput? {
        val ruleConfig = rule.parseConfig(config.ruleConfig)
        if (!rule.isDataNeeded(ruleConfig)) return null
        val state = findPipelineAdmissionRuleStatusByAdmissionRuleConfigId(pipeline, config.id)
        val ruleData = state?.data?.let {
            SlotAdmissionRuleTypedData(
                timestamp = it.timestamp,
                user = it.user,
                data = rule.parseData(it.data),
            )
        }
        return if (ruleData?.data != null && rule.isDataComplete(ruleConfig, ruleData.data)) {
            null
        } else {
            SlotAdmissionRuleInput(
                config = config,
                data = ruleData?.data?.asJson()
            )
        }
    }

    override fun saveAdmissionRuleConfig(config: SlotAdmissionRuleConfig) {
        securityService.checkSlotAccess<SlotUpdate>(config.slot)
        // Controls the name
        config.checkName()
        // TODO Controls the provided configuration
        slotAdmissionRuleConfigRepository.saveAdmissionRuleConfig(config)
        eventPostService.post(environmentsEventsFactory.slotUpdated(config.slot))
    }

    override fun findAdmissionRuleConfigById(id: String): SlotAdmissionRuleConfig? =
        slotAdmissionRuleConfigRepository.findAdmissionRuleConfigById(id)
            ?.takeIf { securityService.isSlotAccessible<ProjectView>(it.slot) }

    override fun getAdmissionRuleConfigs(slot: Slot): List<SlotAdmissionRuleConfig> {
        securityService.checkSlotAccess<SlotView>(slot)
        return slotAdmissionRuleConfigRepository.getAdmissionRuleConfigs(slot)
    }

    override fun deleteAdmissionRuleConfig(config: SlotAdmissionRuleConfig) {
        securityService.checkSlotAccess<SlotUpdate>(config.slot)
        slotAdmissionRuleConfigRepository.deleteAdmissionRuleConfig(config)
        eventPostService.post(environmentsEventsFactory.slotUpdated(config.slot))
    }

    override fun isBuildEligible(slot: Slot, build: Build): Boolean =
        eligibleSlot(slot, build).eligible

    /**
     * Whether [build] may enter [slot], **and** the rules which say it may not.
     *
     * The shared half of [isBuildEligible] and [getEligibleSlotsForBuild]: "is this build
     * eligible" and "why is it not" are the same walk over the same admission rules, and
     * answering the second one separately would mean walking them twice and let the two answers
     * disagree. A slot is eligible exactly when no rule of its own refuses, which is why
     * [EligibleSlot.eligible] is derived from the list here rather than computed beside it.
     */
    private fun eligibleSlot(slot: Slot, build: Build): EligibleSlot {
        securityService.checkSlotAccess<SlotView>(slot)
        // Always checking the project. Not a rule, and so not something to report as one: a slot
        // of another project is not on offer at all rather than on offer and refusing.
        if (build.project != slot.project) {
            return EligibleSlot(slot = slot, eligible = false)
        }
        // Gets all the admission rules
        val configs = slotAdmissionRuleConfigRepository.getAdmissionRuleConfigs(slot)
        // All rules must assert that the build is OK for being a candidate
        val nonEligibleRules = configs.filterNot { config ->
            isBuildEligible(slot, config, build)
        }
        return EligibleSlot(
            slot = slot,
            eligible = nonEligibleRules.isEmpty(),
            nonEligibleRules = nonEligibleRules,
        )
    }

    override fun getEligibleBuilds(
        slot: Slot,
        offset: Int,
        count: Int,
        deployable: Boolean,
    ): PaginatedList<Build> {
        securityService.checkSlotAccess<SlotView>(slot)
        // Gets all the admission rules
        val configs = slotAdmissionRuleConfigRepository.getAdmissionRuleConfigs(slot)
        // Collecting parameters & queries
        val queries = mutableListOf<String>()
        val params = mutableMapOf<String, Any?>()
        configs.forEach { config ->
            fillEligibilityCriteria(slot, config, queries, params, deployable = deployable)
        }
        // Gets all eligible builds in the range
        val range = slotRepository.getEligibleBuilds(
            slot = slot,
            offset = offset,
            size = count,
            queries = queries,
            params = params
        )
        // Gets the whole count
        val total = slotRepository.getCountEligibleBuilds(
            slot = slot,
            queries = queries,
            params = params
        )
        // OK
        return PaginatedList.create(
            items = range,
            offset = offset,
            pageSize = count,
            total = total,
        )
    }

    private fun fillEligibilityCriteria(
        slot: Slot,
        config: SlotAdmissionRuleConfig,
        queries: MutableList<String>,
        params: MutableMap<String, Any?>,
        deployable: Boolean,
    ) {
        val rule = slotAdmissionRuleRegistry.getRule(config.ruleId)
        fillEligibilityCriteria(slot, rule, config.ruleConfig, queries, params, deployable)
    }

    private fun <C : Any, D> fillEligibilityCriteria(
        slot: Slot,
        rule: SlotAdmissionRule<C, D>,
        jsonRuleConfig: JsonNode,
        queries: MutableList<String>,
        params: MutableMap<String, Any?>,
        deployable: Boolean,
    ) {
        val ruleConfig = rule.parseConfig(jsonRuleConfig)
        rule.fillEligibilityCriteria(
            slot = slot,
            config = ruleConfig,
            queries = queries,
            params = params,
            deployable = deployable,
        )
    }

    private fun isBuildEligible(slot: Slot, config: SlotAdmissionRuleConfig, build: Build): Boolean {
        val rule = slotAdmissionRuleRegistry.getRule(config.ruleId)
        return isBuildEligible(slot, rule, config.ruleConfig, build)
    }

    private fun <C : Any, D> isBuildEligible(
        slot: Slot,
        rule: SlotAdmissionRule<C, D>,
        jsonRuleConfig: JsonNode,
        build: Build
    ): Boolean {
        // Parsing the config
        val ruleConfig = rule.parseConfig(jsonRuleConfig)
        // Checking the rule
        return rule.isBuildEligible(build, slot, ruleConfig)
    }

    override fun getSlotById(id: String): Slot {
        return slotRepository.getSlotById(id).apply {
            securityService.checkSlotAccess<SlotView>(this)
        }
    }

    override fun startPipeline(
        slot: Slot,
        build: Build,
        forceDone: Boolean,
        forceDoneMessage: String?,
        skipWorkflows: Boolean,
    ): SlotPipeline {
        securityService.checkSlotAccess<SlotPipelineCreate>(slot)
        // Build must be eligible
        if (!isBuildEligible(slot, build)) {
            throw SlotPipelineBuildNotEligibleException(slot, build)
        }
        // Cancelling all current pipelines
        slotPipelineRepository.forAllActivePipelines(slot) { pipeline ->
            cancelPipeline(pipeline, "Cancelled by more recent pipeline.")
        }
        // Creating the new pipeline
        val pipeline = SlotPipeline(slot = slot, build = build)
        // Saving the pipeline
        slotPipelineRepository.savePipeline(pipeline)
        // Saving the initial change
        slotPipelineChangeRepository.save(
            SlotPipelineChange(
                pipeline = pipeline,
                user = securityService.currentSignature.user.name,
                timestamp = pipeline.start,
                type = SlotPipelineChangeType.STATUS,
                status = pipeline.status,
                message = "Pipeline started",
            )
        )
        // Event linked to the creation of this pipeline
        val event = environmentsEventsFactory.pipelineCreation(pipeline)
        // Workflow triggers
        if (!forceDone) {
            slotWorkflowService.startWorkflowsForPipeline(
                pipeline,
                SlotPipelineStatus.CANDIDATE,
                event
            )
        }
        // Posting the creation of the pipeline
        eventPostService.post(event)
        // Forcing the DONE status
        if (forceDone) {
            // Forcing the deployment in RUNNING state
            runDeployment(
                pipelineId = pipeline.id,
                dryRun = false,
                force = true,
            )
            // Forcing the deployment in DONE state
            val message = forceDoneMessage
                ?.takeIf { it.isNotBlank() }
                ?: "Deployment is forced to done."
            finishDeployment(
                pipelineId = pipeline.id,
                forcing = true,
                message = message,
                skipWorkflows = skipWorkflows,
            )
        }
        // OK
        return findPipelineById(pipeline.id) ?: throw SlotPipelineIdNotFoundException(pipeline.id)
    }

    override fun findSlotsByProject(project: Project, qualifier: String?): Set<Slot> =
        slotRepository.findSlotsByProject(project, qualifier).filter {
            securityService.isSlotAccessible<ProjectView>(it)
        }.toSet()

    override fun getLastDeployedPipeline(slot: Slot): SlotPipeline? =
        slotPipelineRepository.findLastDeployedPipeline(slot)

    override fun getEligibleSlotsForBuild(build: Build): List<EligibleSlot> =
        slotRepository.findSlotsByProject(build.project, qualifier = null).map { slot ->
            eligibleSlot(slot, build)
        }

    override fun findSlotByProjectAndEnvironment(environment: Environment, project: Project, qualifier: String): Slot? =
        slotRepository.findSlotByProjectAndEnvironment(environment, project, qualifier)

    override fun findHighestDeployedSlotPipelinesByBuildAndQualifier(build: Build): Set<SlotPipeline> {
        // Finds the slots for the corresponding project
        val slots: Set<Slot> = findSlotsByProject(build.project)
        // For each slot, gets the last DEPLOYED pipeline and uses it
        // if for the given build
        val lastDeployedPipelines: List<SlotPipeline> = slots.mapNotNull { slot ->
            getLastDeployedPipeline(slot)
        }.filter {
            // Keeping only the pipelines for the given build
            it.build.id() == build.id()
        }
        // Grouping per qualifier
        val lastDeployedPipelinesPerQualifier = lastDeployedPipelines.groupBy { it.slot.qualifier }
        // For each group, gets the pipeline with the highest environment
        val highestPipelinePerQualifier = lastDeployedPipelinesPerQualifier.map { (qualifier, pipelines) ->
            qualifier to pipelines.maxBy { it.slot.environment.order }
        }.toMap()
        // Returning the pipelines
        return highestPipelinePerQualifier.values.toSet()
    }

    override fun findCurrentDeployments(build: Build, qualifier: String?): List<SlotPipeline> {
        // Finds the slots for the corresponding project & qualifier (null meaning any qualifier)
        val slots: Set<Slot> = findSlotsByProject(build.project, qualifier = qualifier)
        // For each slot, gets the last DEPLOYED pipeline and uses it
        // if for the given build
        val lastDeployedPipelines: List<SlotPipeline> = slots.mapNotNull { slot ->
            getLastDeployedPipeline(slot)
        }.filter {
            // Keeping only the pipelines for the given build
            it.build.id() == build.id()
        }
        // Sorting by environment
        return lastDeployedPipelines.sortedByDescending { it.slot.environment.order }
    }

    override fun findPipelines(
        slot: Slot,
        offset: Int,
        size: Int,
        buildId: Int?,
        branchName: String?,
        done: Boolean?
    ): PaginatedList<SlotPipeline> {
        securityService.checkSlotAccess<SlotView>(slot)
        return slotPipelineRepository.findPipelines(
            slot = slot,
            offset = offset,
            size = size,
            buildId = buildId,
            branchName = branchName,
            done = done,
        )
    }

    override fun cancelPipeline(pipeline: SlotPipeline, reason: String) {
        securityService.checkSlotAccess<SlotPipelineCancel>(pipeline.slot)
        changePipeline(
            pipeline = pipeline,
            status = SlotPipelineStatus.CANCELLED,
            type = SlotPipelineChangeType.STATUS,
            message = reason,
        )
        eventPostService.post(environmentsEventsFactory.pipelineCancelled(pipeline))
    }

    private fun changePipeline(
        pipeline: SlotPipeline,
        type: SlotPipelineChangeType,
        status: SlotPipelineStatus,
        message: String,
        override: SlotAdmissionRuleOverride? = null,
    ) {
        val user = securityService.currentSignature.user.name
        val timestamp = Time.now
        slotPipelineChangeRepository.save(
            SlotPipelineChange(
                pipeline = pipeline,
                user = user,
                timestamp = timestamp,
                type = type,
                status = status,
                message = message,
                overrideMessage = override?.message,
            )
        )
        var updatedPipeline = pipeline.withStatus(status)
        if (status.finished) {
            updatedPipeline = updatedPipeline.withEnd(timestamp)
        }
        slotPipelineRepository.savePipeline(updatedPipeline)
    }

    override fun findPipelineById(id: String): SlotPipeline? {
        return slotPipelineRepository.findPipelineById(id)?.apply {
            securityService.checkSlotAccess<SlotView>(slot)
        }
    }

    override fun getPipelineChanges(pipeline: SlotPipeline): List<SlotPipelineChange> {
        securityService.checkSlotAccess<SlotView>(pipeline.slot)
        return slotPipelineChangeRepository.findByPipeline(pipeline)
    }

    override fun getDeploymentRunActionProgress(pipelineId: String): SlotPipelineDeploymentStatusProgress? {
        val pipeline = slotPipelineRepository.getPipelineById(pipelineId)
        securityService.checkSlotAccess<SlotView>(pipeline.slot)

        // Checking the pipeline status
        if (pipeline.status != SlotPipelineStatus.CANDIDATE) {
            return null
        }

        // Admission rules statuses
        val checks = getPipelineAdmissionRuleChecksForAllRules(pipeline).toMutableList()

        // Checking the workflows
        checks += slotWorkflowService.getSlotWorkflowChecks(
            pipeline,
            SlotPipelineStatus.CANDIDATE,
            skipWorkflowId = null
        )

        // Progress
        return deploymentStatusProcessFromChecks(checks)
    }

    override fun runDeployment(
        pipelineId: String,
        dryRun: Boolean,
        skipWorkflowId: String?,
        force: Boolean,
    ): SlotDeploymentActionStatus {
        val pipeline = slotPipelineRepository.getPipelineById(pipelineId)
        securityService.checkSlotAccess<SlotPipelineStart>(pipeline.slot)

        // Always checking the project
        if (pipeline.build.project != pipeline.slot.project) {
            throw SlotPipelineProjectException(pipeline)
        }

        // Checking the pipeline status
        if (pipeline.status != SlotPipelineStatus.CANDIDATE) {
            return SlotDeploymentActionStatus.nok("Cannot run a deployment if not a candidate")
        }

        // Skipping the controls when forcing
        if (!force) {

            // Admission rules statuses
            val admissionRules = slotAdmissionRuleConfigRepository.getAdmissionRuleConfigs(pipeline.slot)
            val rulesChecks = admissionRules.map { admissionRule ->
                val ruleStatus = slotPipelineAdmissionRuleStatusRepository.findStatusByPipelineAndAdmissionRuleConfig(
                    pipeline,
                    admissionRule
                )
                getAdmissionRuleCheck(pipeline, admissionRule, ruleStatus)
            }
            if (rulesChecks.any { !it.ok }) {
                return SlotDeploymentActionStatus.nok("Some admission rules prevent the deployment to start")
            }

            val workflowChecks =
                slotWorkflowService.getSlotWorkflowChecks(pipeline, SlotPipelineStatus.CANDIDATE, skipWorkflowId)
            if (workflowChecks.any { !it.ok }) {
                return SlotDeploymentActionStatus.nok("Some workflows prevent the deployment to start")
            }

        }

        // Actual start
        if (!dryRun) {
            // Marks this pipeline as running
            changePipeline(
                pipeline = pipeline,
                type = SlotPipelineChangeType.STATUS,
                status = SlotPipelineStatus.RUNNING,
                message = "Deployment running",
            )
            // Event linked to the pipeline running
            val event = environmentsEventsFactory.pipelineDeploying(pipeline)
            if (!force) {
                // Workflows
                slotWorkflowService.startWorkflowsForPipeline(
                    pipeline,
                    SlotPipelineStatus.RUNNING,
                    event
                )
            }
            eventPostService.post(event)
        }
        // OK
        return SlotDeploymentActionStatus.ok("Deployment running")
    }

    override fun getAdmissionRuleCheck(
        ruleStatus: SlotPipelineAdmissionRuleStatus,
    ): SlotDeploymentCheck =
        getAdmissionRuleCheck(
            pipeline = ruleStatus.pipeline,
            admissionRule = ruleStatus.admissionRuleConfig,
            ruleStatus = ruleStatus,
        )

    override fun getAdmissionRuleCheck(
        pipeline: SlotPipeline,
        admissionRule: SlotAdmissionRuleConfig
    ): SlotDeploymentCheck {
        val status = slotPipelineAdmissionRuleStatusRepository.findStatusByPipelineAndAdmissionRuleConfig(
            pipeline = pipeline,
            config = admissionRule,
        )
        return getAdmissionRuleCheck(
            pipeline = pipeline,
            admissionRule = admissionRule,
            ruleStatus = status,
        )
    }

    private fun getAdmissionRuleCheck(
        pipeline: SlotPipeline,
        admissionRule: SlotAdmissionRuleConfig,
        ruleStatus: SlotPipelineAdmissionRuleStatus?
    ): SlotDeploymentCheck {
        if (ruleStatus?.override != null) {
            return SlotDeploymentCheck(
                ok = true,
                overridden = true,
                reason = "Rule has been overridden"
            )
        }
        val rule = slotAdmissionRuleRegistry.getRule(admissionRule.ruleId)
        return getAdmissionRuleCheck(
            pipeline,
            rule,
            admissionRule,
            ruleStatus,
        )
    }

    private fun <C : Any, D> getAdmissionRuleCheck(
        pipeline: SlotPipeline,
        rule: SlotAdmissionRule<C, D>,
        admissionRule: SlotAdmissionRuleConfig,
        ruleStatus: SlotPipelineAdmissionRuleStatus?
    ): SlotDeploymentCheck {
        return rule.isBuildDeployable(
            pipeline = pipeline,
            admissionRuleConfig = admissionRule,
            ruleConfig = rule.parseConfig(admissionRule.ruleConfig),
            ruleData = ruleStatus?.data?.let {
                val parsedData = rule.parseData(it.data)
                SlotAdmissionRuleTypedData(
                    timestamp = it.timestamp,
                    user = it.user,
                    data = parsedData,
                )
            }
        )
    }

    override fun getDeploymentFinishActionProgress(pipelineId: String): SlotPipelineDeploymentStatusProgress? {
        val pipeline = slotPipelineRepository.getPipelineById(pipelineId)
        securityService.checkSlotAccess<SlotView>(pipeline.slot)

        // Checking the pipeline status
        if (pipeline.status != SlotPipelineStatus.RUNNING) {
            return null
        }

        // Checking the workflows
        val checks =
            slotWorkflowService.getSlotWorkflowChecks(pipeline, SlotPipelineStatus.RUNNING, skipWorkflowId = null)

        // Progress
        return deploymentStatusProcessFromChecks(checks)
    }

    private fun deploymentStatusProcessFromChecks(checks: List<SlotDeploymentCheck>) =
        SlotPipelineDeploymentStatusProgress(
            ok = checks.all { it.ok },
            overridden = checks.any { it.overridden },
            successCount = checks.count { it.ok },
            totalCount = checks.size,
        )

    override fun finishDeployment(
        pipelineId: String,
        skipWorkflowId: String?,
        forcing: Boolean,
        message: String?,
        skipWorkflows: Boolean,
    ): SlotDeploymentActionStatus {
        val pipeline = slotPipelineRepository.getPipelineById(pipelineId)
        securityService.checkSlotAccess<SlotPipelineFinish>(pipeline.slot)
        // Only last pipeline can be deployed
        val lastPipeline = getCurrentPipeline(pipeline.slot)
        if (lastPipeline?.id != pipeline.id) {
            return SlotDeploymentActionStatus.nok("Only the last pipeline can be deployed.")
        }
        // Checking if pipeline is running
        if (lastPipeline.status != SlotPipelineStatus.RUNNING && !forcing) {
            return SlotDeploymentActionStatus.nok("Pipeline can be deployed only if deployment has been started first.")
        }
        // Checking right to force
        if (forcing) {
            securityService.checkSlotAccess<SlotPipelineOverride>(pipeline.slot)
        }
        // Checking the workflows
        if (!forcing) {
            val workflowChecks = slotWorkflowService.getSlotWorkflowChecks(
                pipeline,
                SlotPipelineStatus.RUNNING,
                skipWorkflowId = skipWorkflowId
            )
            if (workflowChecks.any { !it.ok }) {
                return SlotDeploymentActionStatus.nok("Some workflows prevent the deployment to complete")
            }
        }
        // Actual message
        val actualMessage = message ?: "Deployment finished"
        // Marking the pipeline as deployed
        changePipeline(
            pipeline = pipeline,
            type = SlotPipelineChangeType.STATUS,
            status = SlotPipelineStatus.DONE,
            message = actualMessage,
            override = if (forcing) {
                SlotAdmissionRuleOverride(
                    user = securityService.currentSignature.user.name,
                    timestamp = Time.now,
                    message = message ?: "Deployment was marked done manually."
                )
            } else {
                null
            },
        )
        // Workflows
        val event = environmentsEventsFactory.pipelineDeployed(pipeline)
        if (!skipWorkflows) {
            slotWorkflowService.startWorkflowsForPipeline(
                pipeline,
                SlotPipelineStatus.DONE,
                event
            )
        }
        // Event
        eventPostService.post(event)
        // OK
        return SlotDeploymentActionStatus.ok(actualMessage)
    }

    override fun getPipelineErrorMessage(pipeline: SlotPipeline): String? {
        val checks = mutableListOf<SlotDeploymentCheck>()
        when (pipeline.status) {
            SlotPipelineStatus.CANDIDATE -> {
                checks += getPipelineAdmissionRuleChecksForAllRules(pipeline)
                checks += slotWorkflowService.getSlotWorkflowChecks(pipeline, SlotPipelineStatus.CANDIDATE, null)
            }

            SlotPipelineStatus.RUNNING -> {
                checks += slotWorkflowService.getSlotWorkflowChecks(pipeline, SlotPipelineStatus.RUNNING, null)
            }

            SlotPipelineStatus.DONE -> {
                checks += slotWorkflowService.getSlotWorkflowChecks(pipeline, SlotPipelineStatus.DONE, null)
            }

            SlotPipelineStatus.CANCELLED -> {
                // No error message
            }
        }
        return if (checks.any { !it.ok }) {
            val check = checks.first { !it.ok }
            check.reason
        } else {
            null
        }
    }

    override fun getCurrentPipeline(slot: Slot): SlotPipeline? {
        securityService.checkSlotAccess<SlotView>(slot)
        return findPipelines(slot).pageItems.firstOrNull()
    }

    override fun getPipelineAdmissionRuleStatuses(pipeline: SlotPipeline): List<SlotPipelineAdmissionRuleStatus> {
        securityService.checkSlotAccess<SlotView>(pipeline.slot)
        return slotPipelineAdmissionRuleStatusRepository.findStatusesByPipeline(pipeline)
    }

    override fun findPipelineAdmissionRuleStatusByAdmissionRuleConfigId(
        pipeline: SlotPipeline,
        id: String
    ): SlotPipelineAdmissionRuleStatus? {
        securityService.checkSlotAccess<SlotView>(pipeline.slot)
        val config = slotAdmissionRuleConfigRepository.getAdmissionRuleConfigById(
            pipeline.slot,
            id
        )
        return slotPipelineAdmissionRuleStatusRepository.findStatusByPipelineAndAdmissionRuleConfig(
            pipeline = pipeline,
            config = config,
        )
    }

    override fun overrideAdmissionRule(
        pipeline: SlotPipeline,
        admissionRuleConfig: SlotAdmissionRuleConfig,
        message: String
    ) {
        securityService.checkSlotAccess<SlotPipelineOverride>(pipeline.slot)
        // Checking that we are targeting the same slot
        checkSameSlot(pipeline, admissionRuleConfig)
        // Gets the existing status
        val existing = slotPipelineAdmissionRuleStatusRepository.findStatusByPipelineAndAdmissionRuleConfig(
            pipeline = pipeline,
            config = admissionRuleConfig,
        )
        // Overriding the rule
        val override = SlotAdmissionRuleOverride(
            timestamp = Time.now,
            user = securityService.currentSignature.user.name,
            message = message,
        )
        slotPipelineAdmissionRuleStatusRepository.saveStatus(
            SlotPipelineAdmissionRuleStatus(
                pipeline = pipeline,
                admissionRuleConfig = admissionRuleConfig,
                data = existing?.data,
                override = override,
            )
        )
        // Registering the change
        changePipeline(
            pipeline = pipeline,
            type = SlotPipelineChangeType.RULE_OVERRIDDEN,
            status = pipeline.status,
            message = "Rule overridden",
            override = override,
        )
        eventPostService.post(environmentsEventsFactory.pipelineStatusOverridden(pipeline))
    }

    override fun deleteSlot(slot: Slot) {
        securityService.checkSlotAccess<SlotDelete>(slot)
        eventPostService.post(environmentsEventsFactory.slotDeleted(slot))
        slotRepository.deleteSlot(slot)
    }

    private fun checkSameSlot(
        pipeline: SlotPipeline,
        admissionRuleConfig: SlotAdmissionRuleConfig
    ) {
        if (pipeline.slot.id != admissionRuleConfig.slot.id) {
            throw SlotAdmissionRuleConfigIdNotFoundInSlotException(pipeline, admissionRuleConfig)
        }
    }

    override fun setupAdmissionRule(
        pipeline: SlotPipeline,
        admissionRuleConfig: SlotAdmissionRuleConfig,
        data: JsonNode
    ) {
        securityService.checkSlotAccess<SlotPipelineData>(pipeline.slot)
        // Checking that we are targeting the same slot
        checkSameSlot(pipeline, admissionRuleConfig)
        if (pipeline.status == SlotPipelineStatus.CANDIDATE) {
            // Gets the rule
            val rule = slotAdmissionRuleRegistry.getRule(admissionRuleConfig.ruleId)
            // Checks the rule for the new state
            rule.checkData(admissionRuleConfig.ruleConfig, data)
            // Gets the existing status
            val existing = slotPipelineAdmissionRuleStatusRepository.findStatusByPipelineAndAdmissionRuleConfig(
                pipeline = pipeline,
                config = admissionRuleConfig,
            )
            // Overriding the rule
            slotPipelineAdmissionRuleStatusRepository.saveStatus(
                SlotPipelineAdmissionRuleStatus(
                    pipeline = pipeline,
                    admissionRuleConfig = admissionRuleConfig,
                    data = SlotAdmissionRuleData(
                        timestamp = Time.now,
                        user = securityService.currentSignature.user.name,
                        data = data,
                    ),
                    override = existing?.override,
                )
            )
            // Registering the change
            changePipeline(
                pipeline = pipeline,
                type = SlotPipelineChangeType.RULE_DATA,
                status = pipeline.status,
                message = "Rule data changed",
                override = null,
            )
        } else {
            throw SlotPipelineDataNotOngoingException()
        }
        eventPostService.post(environmentsEventsFactory.pipelineStatusChanged(pipeline))
    }
}