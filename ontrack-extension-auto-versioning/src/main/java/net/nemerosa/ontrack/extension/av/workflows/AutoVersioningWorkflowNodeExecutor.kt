package net.nemerosa.ontrack.extension.av.workflows

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.av.AutoVersioningExtensionFeature
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditService
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningProcessingOutcome
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningProcessingService
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.extension.workflows.execution.AbstractTypedWorkflowNodeExecutor
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorResult
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.events.SerializableEventService
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Component
@Documentation(AutoVersioningWorkflowNodeExecutorData::class)
class AutoVersioningWorkflowNodeExecutor(
    extensionFeature: AutoVersioningExtensionFeature,
    private val autoVersioningProcessingService: AutoVersioningProcessingService,
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val eventTemplatingService: EventTemplatingService,
    private val serializableEventService: SerializableEventService,
    private val autoVersioningAuditService: AutoVersioningAuditService,
) : AbstractTypedWorkflowNodeExecutor<AutoVersioningWorkflowNodeExecutorData>(
    feature = extensionFeature,
    id = "auto-versioning",
    displayName = "Auto-versioning",
    dataType = AutoVersioningWorkflowNodeExecutorData::class,
) {

    override fun validate(data: JsonNode) {
        data.parse<AutoVersioningWorkflowNodeExecutorData>()
    }

    override fun execute(
        workflowInstance: WorkflowInstance,
        data: AutoVersioningWorkflowNodeExecutorData,
        workflowNodeExecutorResultFeedback: (output: JsonNode?) -> Unit
    ): WorkflowNodeExecutorResult {
        val order = securityService.asAdmin {
            createOrder(workflowInstance, data)
        }
        val output = AutoVersioningWorkflowNodeExecutorOutput(
            autoVersioningOrderId = order.uuid,
        )
        workflowNodeExecutorResultFeedback(output.asJson())

        // Starting the audit trail
        autoVersioningAuditService.onCreated(order)

        // Actual auto-versioning process
        val outcome = autoVersioningProcessingService.process(order)

        return if (outcome == AutoVersioningProcessingOutcome.CREATED) {
            WorkflowNodeExecutorResult.success(
                output = output.asJson()
            )
        } else {
            WorkflowNodeExecutorResult.error(
                message = outcome.message,
                output = output.asJson(),
            )
        }
    }

    private fun createOrder(
        workflowInstance: WorkflowInstance,
        data: AutoVersioningWorkflowNodeExecutorData,
    ): AutoVersioningOrder {

        val hydratedEvent = serializableEventService.hydrate(workflowInstance.event)
        val sourceBuild = getSourceBuild(hydratedEvent)

        val resolvedData = data.resolve {
            eventTemplatingService.renderEvent(
                event = hydratedEvent,
                context = emptyMap(),
                template = it,
                renderer = PlainEventRenderer.INSTANCE,
            )
        }
        val targetBranch = getTargetBranch(resolvedData)

        return AutoVersioningOrder(
            uuid = UUID.randomUUID().toString(),
            sourceProject = sourceBuild.project.name,
            sourceBuildId = sourceBuild.id(),
            sourcePromotionRunId = null,
            sourcePromotion = null,
            sourceBackValidation = null,
            qualifier = null,
            branch = targetBranch,
            targetPath = resolvedData.targetPath,
            targetRegex = resolvedData.targetRegex,
            targetProperty = resolvedData.targetProperty,
            targetPropertyRegex = resolvedData.targetPropertyRegex,
            targetPropertyType = resolvedData.targetPropertyType,
            targetVersion = resolvedData.targetVersion,
            autoApproval = resolvedData.autoApproval,
            upgradeBranchPattern = resolvedData.upgradeBranchPattern,
            postProcessing = resolvedData.postProcessing,
            postProcessingConfig = resolvedData.postProcessingConfig,
            validationStamp = resolvedData.validationStamp,
            autoApprovalMode = resolvedData.autoApprovalMode,
            reviewers = resolvedData.reviewers,
            prTitleTemplate = resolvedData.prTitleTemplate,
            prBodyTemplate = resolvedData.prBodyTemplate,
            prBodyTemplateFormat = resolvedData.prBodyTemplateFormat,
            additionalPaths = resolvedData.additionalPaths,
            schedule = null,
            pushMode = resolvedData.pushMode,
        )
    }

    private fun getTargetBranch(
        data: AutoVersioningWorkflowNodeExecutorData
    ): Branch =
        structureService.findBranchByName(
            data.targetProject,
            data.targetBranch
        ).getOrNull() ?: error("Cannot find branch ${data.targetProject}/${data.targetBranch}")

    private fun getSourceBuild(event: Event): Build =
        event.entities[ProjectEntityType.BUILD] as? Build?
            ?: error("Cannot find build in workflow context")
}
