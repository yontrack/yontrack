package net.nemerosa.ontrack.extension.environments.workflows

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.environments.*
import net.nemerosa.ontrack.extension.environments.security.SlotPipelineOverride
import net.nemerosa.ontrack.extension.environments.security.SlotPipelineWorkflowRun
import net.nemerosa.ontrack.extension.environments.security.SlotUpdate
import net.nemerosa.ontrack.extension.environments.security.SlotView
import net.nemerosa.ontrack.extension.environments.service.checkSlotAccess
import net.nemerosa.ontrack.extension.environments.service.isSlotAccessible
import net.nemerosa.ontrack.extension.environments.templating.DeploymentTemplatingContextData
import net.nemerosa.ontrack.extension.environments.templating.DeploymentTemplatingContextHandler
import net.nemerosa.ontrack.extension.environments.trigger.SlotPipelineTrigger
import net.nemerosa.ontrack.extension.environments.trigger.SlotPipelineTriggerData
import net.nemerosa.ontrack.extension.environments.workflows.executors.forSlotWorkflowExecution
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowEngine
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceStatus
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorService
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.SerializableEventService
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.templating.createTemplatingContextData
import net.nemerosa.ontrack.model.trigger.createTriggerData
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class SlotWorkflowServiceImpl(
    private val securityService: SecurityService,
    private val slotWorkflowRepository: SlotWorkflowRepository,
    private val slotWorkflowInstanceRepository: SlotWorkflowInstanceRepository,
    private val workflowEngine: WorkflowEngine,
    private val serializableEventService: SerializableEventService,
    private val slotPipelineTrigger: SlotPipelineTrigger,
    private val deploymentTemplatingContextHandler: DeploymentTemplatingContextHandler,
    private val workflowNodeExecutorService: WorkflowNodeExecutorService,
) : SlotWorkflowService {

    override fun addSlotWorkflow(slotWorkflow: SlotWorkflow) {
        securityService.checkSlotAccess<SlotUpdate>(slotWorkflow.slot)
        workflowNodeExecutorService.validateWorkflowNodes(slotWorkflow.workflow)
        slotWorkflowRepository.addSlotWorkflow(slotWorkflow)
    }

    override fun findSlotWorkflowById(id: String): SlotWorkflow? {
        return slotWorkflowRepository.findSlotWorkflowById(id)
    }

    override fun getSlotWorkflowById(id: String): SlotWorkflow =
        slotWorkflowRepository.getSlotWorkflowById(id)

    override fun getSlotWorkflowsBySlotAndTrigger(slot: Slot, trigger: SlotPipelineStatus): List<SlotWorkflow> {
        securityService.checkSlotAccess<SlotView>(slot)
        return slotWorkflowRepository.getSlotWorkflowsBySlotAndTrigger(slot, trigger)
            .sortedBy { it.workflow.name }
    }

    override fun getSlotWorkflowsBySlot(slot: Slot): List<SlotWorkflow> {
        securityService.checkSlotAccess<SlotView>(slot)
        return slotWorkflowRepository.getSlotWorkflowsBySlot(slot)
            .sortedWith(
                // First by type, then by name
                compareBy(
                    { it.trigger },
                    { it.workflow.name },
                )
            )
    }

    override fun startWorkflow(
        pipeline: SlotPipeline,
        slotWorkflow: SlotWorkflow,
        event: Event,
        trigger: SlotPipelineStatus,
    ): SlotWorkflowInstance {
        securityService.checkSlotAccess<SlotPipelineWorkflowRun>(pipeline.slot)
        if (pipeline.slot.id != slotWorkflow.slot.id) {
            error("Cannot start a workflow for a slot that is different than the one of the pipeline.")
        }
        // Converting the event
        val workflowEvent = serializableEventService.dehydrate(event)
            .forSlotWorkflowExecution(pipeline, slotWorkflow)
        // Starting the workflow
        val workflowInstance = workflowEngine.startWorkflow(
            workflow = slotWorkflow.workflow,
            event = workflowEvent,
            triggerData = slotPipelineTrigger.createTriggerData(
                SlotPipelineTriggerData(
                    pipelineId = pipeline.id,
                    status = trigger,
                )
            ),
            contexts = mapOf(
                "deployment" to deploymentTemplatingContextHandler.createTemplatingContextData(
                    DeploymentTemplatingContextData(
                        slotPipelineId = pipeline.id,
                    )
                )
            ),
            pauseMs = slotWorkflow.pauseMs,
        )
        // Slot workflow instance record
        val slotWorkflowInstance = SlotWorkflowInstance(
            pipeline = pipeline,
            slotWorkflow = slotWorkflow,
            workflowInstance = workflowInstance,
        )
        // Saving the record
        slotWorkflowInstanceRepository.addSlotWorkflowInstance(slotWorkflowInstance)
        // Ok
        return slotWorkflowInstance
    }

    override fun startWorkflowsForPipeline(
        pipeline: SlotPipeline,
        trigger: SlotPipelineStatus,
        event: Event,
    ) {
        securityService.checkSlotAccess<SlotPipelineWorkflowRun>(pipeline.slot)
        val slotWorkflows = getSlotWorkflowsBySlotAndTrigger(pipeline.slot, trigger)
        slotWorkflows.forEach { slotWorkflow ->
            startWorkflow(pipeline, slotWorkflow, event, trigger)
        }
    }

    override fun getSlotWorkflowInstancesByPipeline(pipeline: SlotPipeline): List<SlotWorkflowInstance> {
        securityService.checkSlotAccess<SlotView>(pipeline.slot)
        return slotWorkflowInstanceRepository.getSlotWorkflowInstancesByPipeline(pipeline)
    }

    override fun findSlotWorkflowInstanceById(id: String): SlotWorkflowInstance? =
        slotWorkflowInstanceRepository.findSlotWorkflowInstancesById(id)
            ?.takeIf { securityService.isSlotAccessible<SlotView>(it.pipeline.slot) }

    override fun getSlotWorkflowInstanceById(id: String): SlotWorkflowInstance =
        findSlotWorkflowInstanceById(id) ?: throw SlotWorkflowInstanceIdNotFoundException(id)

    override fun findSlotWorkflowInstanceByPipelineAndSlotWorkflow(
        pipeline: SlotPipeline,
        slotWorkflow: SlotWorkflow
    ): SlotWorkflowInstance? {
        securityService.checkSlotAccess<SlotView>(pipeline.slot)
        return slotWorkflowInstanceRepository.findSlotWorkflowInstanceByPipelineAndSlotWorkflow(
            pipeline,
            slotWorkflow,
        )
    }

    override fun deleteSlotWorkflow(slotWorkflow: SlotWorkflow) {
        securityService.checkSlotAccess<SlotUpdate>(slotWorkflow.slot)
        slotWorkflowRepository.deleteSlotWorkflow(slotWorkflow)
    }

    override fun updateSlotWorkflow(slotWorkflow: SlotWorkflow) {
        securityService.checkSlotAccess<SlotUpdate>(slotWorkflow.slot)
        workflowNodeExecutorService.validateWorkflowNodes(slotWorkflow.workflow)
        slotWorkflowRepository.updateSlotWorkflow(slotWorkflow)
    }

    override fun overrideSlotWorkflowInstance(
        slotWorkflowInstanceId: String,
        message: String
    ): SlotAdmissionRuleOverride {
        val slotWorkflowInstance = getSlotWorkflowInstanceById(slotWorkflowInstanceId)
        securityService.checkSlotAccess<SlotUpdate>(slotWorkflowInstance.pipeline.slot)
        securityService.checkSlotAccess<SlotPipelineOverride>(slotWorkflowInstance.pipeline.slot)
        // Saving the override status
        val user = securityService.currentSignature.user.name
        val timestamp = Time.now
        slotWorkflowInstanceRepository.override(
            slotWorkflowInstance = slotWorkflowInstance,
            user = user,
            timestamp = timestamp,
            message = message,
        )
        // OK
        return SlotAdmissionRuleOverride(
            user = user,
            timestamp = timestamp,
            message = message,
        )
    }

    override fun getSlotWorkflowChecks(
        pipeline: SlotPipeline,
        trigger: SlotPipelineStatus,
        skipWorkflowId: String?
    ): List<SlotDeploymentCheck> {
        securityService.checkSlotAccess<SlotView>(pipeline.slot)
        // Gets all the workflows for this trigger
        val workflows = getSlotWorkflowsBySlotAndTrigger(pipeline.slot, trigger)
            .filter { skipWorkflowId.isNullOrBlank() || skipWorkflowId != it.id }
        // Gets the check for each of them
        return workflows.map { getSlotWorkflowCheck(pipeline, it) }
    }

    override fun getSlotWorkflowCheck(
        pipeline: SlotPipeline,
        slotWorkflow: SlotWorkflow
    ): SlotDeploymentCheck {
        // Gets the instance for this workflow (if any)
        val slotWorkflowInstance = findSlotWorkflowInstanceByPipelineAndSlotWorkflow(
            pipeline, slotWorkflow
        )
        // If present, computing the check
        return if (slotWorkflowInstance != null) {
            if (slotWorkflowInstance.override != null) {
                SlotDeploymentCheck(
                    ok = true,
                    overridden = true,
                    reason = "Workflow was overridden",
                )
            } else {
                when (slotWorkflowInstance.workflowInstance.status) {
                    WorkflowInstanceStatus.SUCCESS -> SlotDeploymentCheck.ok()
                    WorkflowInstanceStatus.STARTED -> SlotDeploymentCheck.nok("Workflow has started")
                    WorkflowInstanceStatus.RUNNING -> SlotDeploymentCheck.nok("Workflow is running")
                    WorkflowInstanceStatus.STOPPED -> SlotDeploymentCheck.nok("Workflow has been stopped")
                    WorkflowInstanceStatus.ERROR -> SlotDeploymentCheck.nok("Workflow is in error")
                }
            }
        }
        // If not present, the workflow has not started at all
        else {
            SlotDeploymentCheck(
                ok = false,
                overridden = false,
                reason = "Workflow has not started",
            )
        }
    }
}