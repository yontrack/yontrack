package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.claimed
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.payload
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.sha256
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.extension.environments.SlotPipeline
import net.nemerosa.ontrack.extension.environments.SlotPipelineChangeType
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.extension.environments.events.EnvironmentsEvents
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.model.events.Event
import org.springframework.stereotype.Component

/**
 * Entries of the deployments of a build: every transition of its slot pipelines, the data and the
 * overrides of their admission rules, the overrides of their slot workflows, and their deletion.
 *
 * The events carry the ID of the pipeline: what they do not carry — its number, the time and user
 * of a change, which a caller can back-date — is read from the pipeline, in the transaction of the
 * change.
 */
@Component
class DeploymentTrailEventMapper(
    private val slotService: SlotService,
) : TrailEventMapper {

    /**
     * Types of the entries of the transitions of a pipeline, by event type, with the status they
     * lead to.
     */
    private val transitions: Map<String, Pair<String, SlotPipelineStatus>> = mapOf(
        EnvironmentsEvents.PIPELINE_CREATION.id to (TrailEntryTypes.DEPLOYMENT_CREATED to SlotPipelineStatus.CANDIDATE),
        EnvironmentsEvents.PIPELINE_DEPLOYING.id to (TrailEntryTypes.DEPLOYMENT_RUNNING to SlotPipelineStatus.RUNNING),
        EnvironmentsEvents.PIPELINE_DEPLOYED.id to (TrailEntryTypes.DEPLOYMENT_DONE to SlotPipelineStatus.DONE),
        EnvironmentsEvents.PIPELINE_CANCELLED.id to (TrailEntryTypes.DEPLOYMENT_CANCELLED to SlotPipelineStatus.CANCELLED),
        EnvironmentsEvents.PIPELINE_FAILED.id to (TrailEntryTypes.DEPLOYMENT_FAILED to SlotPipelineStatus.FAILED),
    )

    override val eventTypes: Set<String> = transitions.keys + setOf(
        EnvironmentsEvents.PIPELINE_STATUS_CHANGED.id,
        EnvironmentsEvents.PIPELINE_STATUS_OVERRIDDEN.id,
        EnvironmentsEvents.PIPELINE_WORKFLOW_OVERRIDDEN.id,
        EnvironmentsEvents.PIPELINE_DELETED.id,
    )

    override fun map(event: Event): List<TrailEntryRequest> {
        val pipeline = slotService.findPipelineById(event.getValue(EnvironmentsEvents.EVENT_PIPELINE_ID))
            ?: return emptyList()
        val transition = transitions[event.eventType.id]
        return when {
            transition != null -> transition(pipeline, transition.first, transition.second)
            event.eventType.id == EnvironmentsEvents.PIPELINE_STATUS_CHANGED.id -> ruleData(event, pipeline)
            event.eventType.id == EnvironmentsEvents.PIPELINE_STATUS_OVERRIDDEN.id -> ruleOverridden(event, pipeline)
            event.eventType.id == EnvironmentsEvents.PIPELINE_WORKFLOW_OVERRIDDEN.id -> pipeline.entry(
                TrailEntryTypes.DEPLOYMENT_WORKFLOW_OVERRIDDEN,
                "slotWorkflow" to mapOf(
                    "id" to event.value(EnvironmentsEvents.EVENT_SLOT_WORKFLOW_ID),
                    "instanceId" to event.value(EnvironmentsEvents.EVENT_SLOT_WORKFLOW_INSTANCE_ID),
                    "workflow" to event.value(EnvironmentsEvents.EVENT_WORKFLOW_NAME),
                ),
                "message" to event.value(EnvironmentsEvents.EVENT_OVERRIDE_MESSAGE),
            )

            event.eventType.id == EnvironmentsEvents.PIPELINE_DELETED.id -> pipeline.entry(
                TrailEntryTypes.DEPLOYMENT_DELETED,
                "status" to pipeline.status.name,
            )

            else -> emptyList()
        }
    }

    /**
     * A transition is claimed by the change of status which led to it — the only one to this status,
     * a pipeline never going through a status twice.
     */
    private fun transition(pipeline: SlotPipeline, type: String, status: SlotPipelineStatus): List<TrailEntryRequest> {
        val change = slotService.getPipelineChanges(pipeline)
            .firstOrNull { it.type == SlotPipelineChangeType.STATUS && it.status == status }
        return pipeline.entry(
            type,
            "message" to change?.message,
            "claimed" to change?.let { claimed(it.timestamp, it.user) },
        )
    }

    private fun ruleData(event: Event, pipeline: SlotPipeline): List<TrailEntryRequest> {
        val status = slotService.findPipelineAdmissionRuleStatusByAdmissionRuleConfigId(
            pipeline,
            event.getValue(EnvironmentsEvents.EVENT_ADMISSION_RULE_CONFIG_ID)
        )
        val data = status?.data
        return pipeline.entry(
            TrailEntryTypes.DEPLOYMENT_RULE_DATA,
            "rule" to event.rule(),
            "data" to data?.let { mapOf("sha256" to sha256(it.data)) },
            "claimed" to data?.let { claimed(it.timestamp, it.user) },
        )
    }

    private fun ruleOverridden(event: Event, pipeline: SlotPipeline): List<TrailEntryRequest> {
        val status = slotService.findPipelineAdmissionRuleStatusByAdmissionRuleConfigId(
            pipeline,
            event.getValue(EnvironmentsEvents.EVENT_ADMISSION_RULE_CONFIG_ID)
        )
        val override = status?.override
        return pipeline.entry(
            TrailEntryTypes.DEPLOYMENT_RULE_OVERRIDDEN,
            "rule" to event.rule(),
            "message" to event.value(EnvironmentsEvents.EVENT_OVERRIDE_MESSAGE),
            "claimed" to override?.let { claimed(it.timestamp, it.user) },
        )
    }

    private fun Event.rule(): Map<String, Any?> = mapOf(
        "id" to value(EnvironmentsEvents.EVENT_ADMISSION_RULE_CONFIG_ID),
        "name" to value(EnvironmentsEvents.EVENT_ADMISSION_RULE_NAME),
        "ruleId" to value(EnvironmentsEvents.EVENT_ADMISSION_RULE_ID),
    )

    private fun SlotPipeline.entry(type: String, vararg properties: Pair<String, Any?>) = listOf(
        TrailEntryRequest(
            build = build,
            type = type,
            payload = payload(
                "deployment" to mapOf(
                    "id" to id,
                    "number" to number,
                    "environment" to slot.environment.name,
                    "slot" to mapOf(
                        "id" to slot.id,
                        "qualifier" to slot.qualifier,
                    ),
                ),
                *properties,
            ),
        )
    )
}
