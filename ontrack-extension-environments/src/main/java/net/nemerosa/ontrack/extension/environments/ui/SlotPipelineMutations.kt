package net.nemerosa.ontrack.extension.environments.ui

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.environments.SlotDeploymentActionStatus
import net.nemerosa.ontrack.extension.environments.SlotPipeline
import net.nemerosa.ontrack.extension.environments.SlotPipelineDataInputValue
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.graphql.schema.Mutation
import net.nemerosa.ontrack.graphql.support.ListRef
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

@Component
class SlotPipelineMutations(
    private val structureService: StructureService,
    private val slotService: SlotService,
) : TypedMutationProvider() {

    override val mutations: List<Mutation> = listOf(
        simpleMutation(
            name = "startSlotPipeline",
            description = "Starts a pipeline for a slot and a build",
            input = StartSlotPipelineInput::class,
            outputName = "pipeline",
            outputDescription = "Created pipeline",
            outputType = SlotPipeline::class,
        ) { input ->
            val slot = slotService.getSlotById(input.slotId)
            val build = structureService.getBuild(ID.of(input.buildId))
            slotService.startPipeline(
                slot = slot,
                build = build,
                forceDone = input.forceDone ?: false,
                forceDoneMessage = input.forceDoneMessage,
                skipWorkflows = input.skipWorkflows ?: false,
            )
        },
        simpleMutation(
            name = "startSlotPipelineDeployment",
            description = "Starts the deployment of a pipeline",
            input = StartSlotPipelineDeploymentInput::class,
            outputName = "deploymentStatus",
            outputDescription = "Pipeline deployment status",
            outputType = SlotDeploymentActionStatus::class,
        ) { input ->
            slotService.runDeployment(
                pipelineId = input.pipelineId,
                dryRun = false,
            )
        },
        simpleMutation(
            name = "finishSlotPipelineDeployment",
            description = "Finishes the deployment of a pipeline",
            input = FinishSlotPipelineDeploymentInput::class,
            outputName = "finishStatus",
            outputDescription = "Status of the deployment",
            outputType = SlotDeploymentActionStatus::class,
        ) { input ->
            slotService.finishDeployment(
                pipelineId = input.pipelineId,
                forcing = input.forcing,
                message = input.message,
            )
        },
        simpleMutation(
            name = "failSlotPipeline",
            description = "Marks a running pipeline as failed",
            input = FailSlotPipelineInput::class,
            outputName = "failStatus",
            outputDescription = "Status of the action",
            outputType = SlotDeploymentActionStatus::class,
        ) { input ->
            slotService.failPipeline(
                pipelineId = input.pipelineId,
                message = input.message,
            )
        },
        unitMutation(
            name = "cancelSlotPipeline",
            description = "Cancelling a pipeline",
            input = CancelSlotPipelineInput::class,
        ) { input ->
            val pipeline = slotService.findPipelineById(input.pipelineId)
            pipeline?.let {
                slotService.cancelPipeline(pipeline, input.reason)
            }
        },
        unitMutation(
            name = "updatePipelineData",
            description = "Updating the pipeline admission data",
            input = UpdatePipelineDataInput::class
        ) { input ->
            val pipeline = slotService.findPipelineById(input.pipelineId)
            pipeline?.let {
                val configs = slotService.getAdmissionRuleConfigs(pipeline.slot).associateBy { it.id }
                input.values.forEach { input ->
                    val config = configs[input.configId]
                        ?: throw SlotPipelineDataInputConfigNotFoundException(input.configId)
                    slotService.setupAdmissionRule(
                        pipeline = pipeline,
                        admissionRuleConfig = config,
                        data = input.data,
                    )
                }
            }
        },
        unitMutation(
            name = "overridePipelineRule",
            description = "Overriding a rule into a pipeline",
            input = OverridePipelineRuleInput::class
        ) { input ->
            val pipeline = slotService.findPipelineById(input.pipelineId)
            val config = slotService.findAdmissionRuleConfigById(input.admissionRuleConfigId)
            if (pipeline != null && config != null) {
                slotService.overrideAdmissionRule(
                    pipeline = pipeline,
                    admissionRuleConfig = config,
                    message = input.message,
                )
            }
        },
        unitMutation(
            name = "deleteDeployment",
            description = "Deletes a deployment",
            input = DeleteDeploymentInput::class
        ) { input ->
            slotService.deleteDeployment(input.deploymentId)
        },
    )
}

data class StartSlotPipelineInput(
    val slotId: String,
    val buildId: Int,
    val forceDone: Boolean? = false,
    val forceDoneMessage: String? = null,
    @APIDescription("Option to skip the workflows on DONE when forcing the deployment")
    val skipWorkflows: Boolean? = false,
)

data class StartSlotPipelineDeploymentInput(
    val pipelineId: String,
)

data class FinishSlotPipelineDeploymentInput(
    val pipelineId: String,
    val forcing: Boolean = false,
    val message: String? = null,
)

data class FailSlotPipelineInput(
    @APIDescription("ID of the running pipeline to mark as failed")
    val pipelineId: String,
    @APIDescription("Optional message recorded in the pipeline's history")
    val message: String? = null,
)

data class CancelSlotPipelineInput(
    val pipelineId: String,
    val reason: String,
)

data class UpdatePipelineDataInput(
    val pipelineId: String,
    @ListRef(embedded = true)
    val values: List<SlotPipelineDataInputValue>,
)

data class OverridePipelineRuleInput(
    val pipelineId: String,
    val admissionRuleConfigId: String,
    val message: String,
)

data class DeleteDeploymentInput(
    val deploymentId: String,
)
