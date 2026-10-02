package net.nemerosa.ontrack.extension.environments.events

import net.nemerosa.ontrack.extension.environments.Environment
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotPipeline
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowInstance
import net.nemerosa.ontrack.model.events.Event

interface EnvironmentsEventsFactory {

    fun environmentCreation(environment: Environment): Event
    fun environmentUpdated(environment: Environment): Event
    fun environmentDeleted(environment: Environment): Event

    fun slotCreation(slot: Slot): Event
    fun slotUpdated(slot: Slot): Event
    fun slotDeleted(slot: Slot): Event

    fun pipelineCreation(pipeline: SlotPipeline): Event
    fun pipelineDeploying(pipeline: SlotPipeline): Event
    fun pipelineDeployed(pipeline: SlotPipeline): Event
    fun pipelineCancelled(pipeline: SlotPipeline): Event
    fun pipelineFailed(pipeline: SlotPipeline): Event
    fun pipelineStatusOverridden(pipeline: SlotPipeline): Event
    fun pipelineStatusChanged(pipeline: SlotPipeline): Event

    /**
     * The [pipeline] is about to be deleted.
     */
    fun pipelineDeleted(pipeline: SlotPipeline): Event

    /**
     * The [slotWorkflowInstance] has been overridden by [user] with [message].
     */
    fun pipelineWorkflowOverridden(slotWorkflowInstance: SlotWorkflowInstance, user: String, message: String): Event
}