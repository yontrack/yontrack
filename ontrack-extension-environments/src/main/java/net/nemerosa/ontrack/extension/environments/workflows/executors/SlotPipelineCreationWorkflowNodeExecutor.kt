package net.nemerosa.ontrack.extension.environments.workflows.executors

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.environments.EnvironmentsExtensionFeature
import net.nemerosa.ontrack.extension.environments.EnvironmentsLicense
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.service.EnvironmentService
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.environments.service.getPipelineById
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutor
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorConfigException
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorResult
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.events.SerializableEvent
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

@Component
@Documentation(SlotPipelineCreationWorkflowNodeExecutorData::class)
class SlotPipelineCreationWorkflowNodeExecutor(
    extensionFeature: EnvironmentsExtensionFeature,
    private val slotService: SlotService,
    private val environmentService: EnvironmentService,
    private val structureService: StructureService,
    environmentsLicense: EnvironmentsLicense,
) : AbstractExtension(extensionFeature), WorkflowNodeExecutor {

    override val enabled: Boolean = environmentsLicense.environmentFeatureEnabled

    override val id: String = "slot-pipeline-creation"
    override val displayName: String = "Pipeline creation"

    override fun validate(data: JsonNode) {
        val parsed = data.parse<SlotPipelineCreationWorkflowNodeExecutorData>()
        if (parsed.environment.isBlank()) {
            throw WorkflowNodeExecutorConfigException("Missing required environment name for slot pipeline creation")
        }
    }

    override fun execute(
        workflowInstance: WorkflowInstance,
        workflowNodeId: String,
        workflowNodeExecutorResultFeedback: (output: JsonNode?) -> Unit
    ): WorkflowNodeExecutorResult {
        val (environment, configuredQualifier) = workflowInstance.workflow.getNode(workflowNodeId).data.parse<SlotPipelineCreationWorkflowNodeExecutorData>()

        // Getting the target environment
        val targetEnvironment = environmentService.findByName(environment)
            ?: return WorkflowNodeExecutorResult.error(
                "Environment name not found: $environment",
                output = null
            )

        val (build, buildQualifier) = getBuildFromContext(workflowInstance.event)
        // Finding the target slot for the same project
        val qualifier = configuredQualifier ?: buildQualifier
        val targetSlot = slotService.findSlotsByEnvironment(targetEnvironment).find {
            it.project.id() == build.project.id() && it.qualifier == qualifier
        }
            ?: return WorkflowNodeExecutorResult.error(
                "Cannot find slot for environment = ${environment}, project = ${build.project.name}, qualifier = $qualifier",
                output = null,
            )
        // Is the build eligible for the target slot?
        if (!slotService.isBuildEligible(targetSlot, build)) {
            return WorkflowNodeExecutorResult.error(
                "Build is not eligible for target slot",
                output = null,
            )
        }
        // Creating the pipeline
        val targetPipeline = slotService.startPipeline(targetSlot, build)
        // OK
        return WorkflowNodeExecutorResult.success(
            output = SlotPipelineCreationWorkflowNodeExecutorOutput(
                targetPipelineId = targetPipeline.id,
            ).asJson(),
            event = workflowInstance.event.forSlotPipeline(targetPipeline),
        )
    }

    private fun getBuildFromContext(serializableEvent: SerializableEvent): QualifiedBuild {
        val pipelineId = serializableEvent.findValue(SlotWorkflowContext.EVENT_SLOT_PIPELINE_ID)
        val buildId = serializableEvent.findEntityId(ProjectEntityType.BUILD)
        return if (!pipelineId.isNullOrBlank()) {
            getBuildFromSlotPipelineWorkflowContext(pipelineId)
        } else if (buildId != null) {
            getBuildFromEvent(buildId)
        } else {
            error("Cannot get build from workflow context")
        }
    }

    private fun getBuildFromEvent(buildId: Int): QualifiedBuild {
        val build = structureService.getBuild(ID.of(buildId))
        return QualifiedBuild(
            build = build,
            qualifier = Slot.DEFAULT_QUALIFIER,
        )
    }

    private fun getBuildFromSlotPipelineWorkflowContext(pipelineId: String): QualifiedBuild {
        val pipeline = slotService.getPipelineById(pipelineId)
        return QualifiedBuild(
            build = pipeline.build,
            qualifier = pipeline.slot.qualifier,
        )
    }

    private data class QualifiedBuild(
        val build: Build,
        val qualifier: String,
    )
}