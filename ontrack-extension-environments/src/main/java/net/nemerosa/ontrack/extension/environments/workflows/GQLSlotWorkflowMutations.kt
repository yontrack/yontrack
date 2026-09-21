package net.nemerosa.ontrack.extension.environments.workflows

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.environments.service.getPipelineById
import net.nemerosa.ontrack.extension.workflows.registry.WorkflowParser
import net.nemerosa.ontrack.graphql.schema.Mutation
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import org.springframework.stereotype.Component
import java.util.*

@Component
class GQLSlotWorkflowMutations(
    val slotService: SlotService,
    val slotWorkflowService: SlotWorkflowService,
) : TypedMutationProvider() {
    override val mutations: List<Mutation> = listOf(
        simpleMutation(
            name = "addSlotWorkflow",
            description = "Add a workflow into an existing slot",
            input = AddSlotWorkflowInput::class,
            outputType = SlotWorkflow::class,
            outputName = "slotWorkflow",
            outputDescription = "Created slot workflow"
        ) { input ->
            val slot = slotService.getSlotById(input.slotId)
            val slotWorkflow = SlotWorkflow(
                slot = slot,
                trigger = input.trigger,
                workflow = WorkflowParser.parseYamlWorkflow(input.workflowYaml),
            )
            slotWorkflowService.addSlotWorkflow(slotWorkflow)
            slotWorkflow
        },
        simpleMutation(
            name = "saveSlotWorkflow",
            description = "Add or saves a workflow into an existing slot",
            input = SaveSlotWorkflowInput::class,
            outputType = SlotWorkflow::class,
            outputName = "slotWorkflow",
            outputDescription = "Created or updated slot workflow"
        ) { input ->
            val slot = slotService.getSlotById(input.slotId)
            val slotWorkflow = SlotWorkflow(
                id = input.id ?: UUID.randomUUID().toString(),
                slot = slot,
                trigger = input.trigger,
                workflow = WorkflowParser.parseJsonWorkflow(input.workflow),
            )
            slotWorkflowService.addSlotWorkflow(slotWorkflow)
            slotWorkflow
        },
        unitMutation(
            name = "deleteSlotWorkflow",
            description = "Deletes a workflow from a slot",
            input = DeleteSlotWorkflowInput::class,
        ) { input ->
            val slotWorkflow = slotWorkflowService.getSlotWorkflowById(input.id)
            slotWorkflowService.deleteSlotWorkflow(slotWorkflow)
        },
        unitMutation(
            name = "overridePipelineWorkflow",
            description = "Overrides the execution of a workflow for a pipeline",
            input = OverridePipelineWorkflowInput::class,
        ) { input ->
            val pipeline = slotService.getPipelineById(input.pipelineId)
            val slotWorkflow = slotWorkflowService.getSlotWorkflowById(input.slotWorkflowId)
            val slotWorkflowInstance = slotWorkflowService.findSlotWorkflowInstanceByPipelineAndSlotWorkflow(
                pipeline = pipeline,
                slotWorkflow = slotWorkflow,
            )
            if (slotWorkflowInstance != null) {
                slotWorkflowService.overrideSlotWorkflowInstance(
                    slotWorkflowInstanceId = slotWorkflowInstance.id,
                    message = input.message,
                )
            }
        },
    )
}

data class AddSlotWorkflowInput(
    val slotId: String,
    val trigger: SlotPipelineStatus,
    val workflowYaml: String,
)

data class SaveSlotWorkflowInput(
    val id: String?,
    val slotId: String,
    val trigger: SlotPipelineStatus,
    val workflow: JsonNode,
)

data class DeleteSlotWorkflowInput(
    val id: String,
)

data class OverridePipelineWorkflowInput(
    val pipelineId: String,
    val slotWorkflowId: String,
    val message: String,
)
