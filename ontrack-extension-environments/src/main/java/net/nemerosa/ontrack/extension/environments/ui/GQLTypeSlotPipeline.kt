package net.nemerosa.ontrack.extension.environments.ui

import graphql.Scalars.GraphQLBoolean
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.environments.*
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowInstance
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowService
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.*
import org.springframework.stereotype.Component

@Component
class GQLTypeSlotPipeline(
    private val gqlTypeSlotPipelineChange: GQLTypeSlotPipelineChange,
    private val slotService: SlotService,
    private val slotWorkflowService: SlotWorkflowService,
) : GQLType {

    override fun getTypeName(): String = SlotPipeline::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Pipeline for a slot")
            .idFieldForString(Slot::id)
            .intField(SlotPipeline::number)
            .localDateTimeField(SlotPipeline::start)
            .localDateTimeField(SlotPipeline::end)
            .enumField(SlotPipeline::status)
            .field(SlotPipeline::slot)
            .field(SlotPipeline::build)
            // Is the pipeline running?
            .field {
                it.name("finished")
                    .description("Is the pipeline finished?")
                    .type(GraphQLBoolean.toNotNull())
                    .dataFetcher { env ->
                        val pipeline: SlotPipeline = env.getSource()!!
                        pipeline.status.finished
                    }
            }
            // Last change
            .field {
                it.name("lastChange")
                    .description("Last change having occurred to the pipeline")
                    .type(gqlTypeSlotPipelineChange.typeRef)
                    .dataFetcher { env ->
                        val pipeline: SlotPipeline = env.getSource()!!
                        slotService.getPipelineChanges(pipeline).firstOrNull()
                    }
            }
            // All changes
            .listFieldGetter<SlotPipeline, SlotPipelineChange>(
                name = "changes",
                description = "Changes having occurred to the pipeline"
            ) { pipeline ->
                slotService.getPipelineChanges(pipeline)
            }
            // Required inputs
            .listFieldGetter<SlotPipeline, SlotAdmissionRuleInput>(
                name = "requiredInputs",
                description = "List of required inputs for the admission rules"
            ) { pipeline ->
                slotService.getRequiredInputs(pipeline)
            }
            // List of workflow instances
            .listFieldGetter<SlotPipeline, SlotWorkflowInstance>(
                name = "slotWorkflowInstances",
                description = "List of workflow instances for this pipeline",
            ) { pipeline ->
                slotWorkflowService.getSlotWorkflowInstancesByPipeline(pipeline)
            }
            // All the checks for the admission rules for this deployments
            .listFieldGetter<SlotPipeline, SlotPipelineAdmissionRuleStatus>(
                name = "admissionRules",
                description = "All the checks for the admission rules for this deployments"
            ) { pipeline ->
                slotService.getAdmissionRuleConfigs(pipeline.slot).map { rule ->
                    val status = slotService.findPipelineAdmissionRuleStatusByAdmissionRuleConfigId(
                        pipeline,
                        rule.id,
                    )
                    status ?: SlotPipelineAdmissionRuleStatus(
                        pipeline = pipeline,
                        admissionRuleConfig = rule,
                        data = null,
                        override = null,
                    )
                }
            }
            // Run action
            .fieldGetter<SlotPipeline, SlotPipelineDeploymentStatusProgress>(
                name = "runAction",
                description = "Can this deployment be actioned into a running state?",
                nullable = true,
            ) { pipeline, _ ->
                slotService.getDeploymentRunActionProgress(pipeline.id)
            }
            // Finish action
            .fieldGetter<SlotPipeline, SlotPipelineDeploymentStatusProgress>(
                name = "finishAction",
                description = "Can this deployment be completed?",
                nullable = true,
            ) { pipeline, _ ->
                slotService.getDeploymentFinishActionProgress(pipeline.id)
            }
            // Error message
            .fieldGetter<SlotPipeline, String>(
                name = "errorMessage",
                description = "Reason of the first failed check of the current phase, if any. " +
                        "A pending check - a workflow still running, an approval still to give - is not an error: " +
                        "see pendingMessage.",
                nullable = true,
            ) { pipeline, _ ->
                slotService.getPipelineErrorMessage(pipeline)
            }
            // Pending message
            .fieldGetter<SlotPipeline, String>(
                name = "pendingMessage",
                description = "Reason of the first pending check of the current phase - a workflow still running, " +
                        "an approval still to give - when no check has failed.",
                nullable = true,
            ) { pipeline, _ ->
                slotService.getPipelinePendingMessage(pipeline)
            }
            // OK
            .build()
}