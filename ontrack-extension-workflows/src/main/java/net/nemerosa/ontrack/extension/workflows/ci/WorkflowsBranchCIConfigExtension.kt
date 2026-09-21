package net.nemerosa.ontrack.extension.workflows.ci

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.mergeMap
import net.nemerosa.ontrack.extension.config.extensions.CIConfigExtension
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionFilter
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionOrigins
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionService
import net.nemerosa.ontrack.extension.notifications.subscriptions.subscribe
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.extension.workflows.WorkflowsExtensionFeature
import net.nemerosa.ontrack.extension.workflows.definition.Workflow
import net.nemerosa.ontrack.extension.workflows.notifications.WorkflowNotificationChannel
import net.nemerosa.ontrack.extension.workflows.notifications.WorkflowNotificationChannelConfig
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.exceptions.PromotionLevelNotFoundException
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
class WorkflowsBranchCIConfigExtension(
    workflowsExtensionFeature: WorkflowsExtensionFeature,
    private val structureService: StructureService,
    private val eventSubscriptionService: EventSubscriptionService,
    private val workflowNotificationChannel: WorkflowNotificationChannel,
) : AbstractExtension(workflowsExtensionFeature), CIConfigExtension<WorkflowsBranchCIConfig> {

    override val id: String = "workflows"

    override val projectEntityTypes: Set<ProjectEntityType> = setOf(ProjectEntityType.BRANCH)

    override fun createJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(WorkflowsBranchCIConfig::class)

    override fun parseData(data: JsonNode): WorkflowsBranchCIConfig = data.parse()

    override fun mergeConfig(
        defaults: WorkflowsBranchCIConfig,
        custom: JsonNode
    ): WorkflowsBranchCIConfig {
        val parsedCustom = custom.parse<WorkflowsBranchCIConfig>()
        return WorkflowsBranchCIConfig(
            promotions = mergeMap(
                target = defaults.promotions,
                changes = parsedCustom.promotions,
            ) { e, existing -> existing + e }
        )
    }

    override fun configure(
        entity: ProjectEntity,
        data: WorkflowsBranchCIConfig
    ) {
        val branch = entity as Branch
        data.promotions.forEach { (name, config) ->
            val promotion =
                structureService.findPromotionLevelByName(
                    project = branch.project.name,
                    branch = branch.name,
                    promotionLevel = name
                ).getOrNull()
                    ?: throw PromotionLevelNotFoundException(branch.project.name, branch.name, name)
            configurePromotion(promotion, config)
        }
    }

    private fun configurePromotion(
        promotion: PromotionLevel,
        config: List<Workflow>
    ) {
        // Deletes existing workflow subscriptions
        cleanupPromotionWorkflows(promotion)
        // Creating the new workflow subscriptions
        config.forEach { workflow ->
            configurePromotionWorkflow(promotion, workflow)
        }
    }

    private fun cleanupPromotionWorkflows(promotion: PromotionLevel) {
        val workflowSubs = eventSubscriptionService.filterSubscriptions(
            filter = EventSubscriptionFilter(
                entity = promotion.toProjectEntityID(),
                channel = workflowNotificationChannel.type
            )
        ).pageItems
        workflowSubs.forEach { workflowSub ->
            eventSubscriptionService.deleteSubscriptionByName(promotion, workflowSub.name)
        }
    }

    private fun configurePromotionWorkflow(
        promotion: PromotionLevel,
        workflow: Workflow
    ) {
        eventSubscriptionService.subscribe(
            name = workflow.name,
            channel = workflowNotificationChannel,
            channelConfig = WorkflowNotificationChannelConfig(workflow),
            projectEntity = promotion,
            keywords = null,
            origin = EventSubscriptionOrigins.CI,
            contentTemplate = null,
            EventFactory.NEW_PROMOTION_RUN,
        )
    }
}