package net.nemerosa.ontrack.extension.environments.events

import net.nemerosa.ontrack.extension.environments.Environment
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotPipeline
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.support.StartupService
import org.springframework.stereotype.Component

@Component
class EnvironmentsEventsFactoryImpl(
    private val eventFactory: EventFactory,
    private val securityService: SecurityService,
) : EnvironmentsEventsFactory, StartupService {

    private fun Event.EventBuilder.withEnvironment(environment: Environment) = this
        .with(EnvironmentsEvents.EVENT_ENVIRONMENT_ID, environment.id)
        .with(EnvironmentsEvents.EVENT_ENVIRONMENT_NAME, environment.name)

    private fun Event.EventBuilder.withSlot(slot: Slot) = this
        .withEnvironment(slot.environment)
        .withProject(slot.project)
        .with(EnvironmentsEvents.EVENT_SLOT_ID, slot.id)

    private fun Event.EventBuilder.withPipeline(pipeline: SlotPipeline) = this
        .withSlot(pipeline.slot)
        .withBuild(pipeline.build)
        .with(EnvironmentsEvents.EVENT_PIPELINE_ID, pipeline.id)

    override fun environmentCreation(environment: Environment): Event =
        Event.of(EnvironmentsEvents.ENVIRONMENT_CREATION)
            .withEnvironment(environment)
            .build()

    override fun environmentUpdated(environment: Environment): Event =
        Event.of(EnvironmentsEvents.ENVIRONMENT_UPDATED)
            .withEnvironment(environment)
            .build()

    override fun environmentDeleted(environment: Environment): Event =
        Event.of(EnvironmentsEvents.ENVIRONMENT_DELETED)
            .with(EnvironmentsEvents.EVENT_ENVIRONMENT_NAME, environment.name)
            .build()

    override fun slotCreation(slot: Slot): Event =
        Event.of(EnvironmentsEvents.SLOT_CREATION)
            .withSlot(slot)
            .build()

    override fun slotUpdated(slot: Slot): Event =
        Event.of(EnvironmentsEvents.SLOT_UPDATED)
            .withSlot(slot)
            .build()

    override fun slotDeleted(slot: Slot): Event =
        Event.of(EnvironmentsEvents.SLOT_DELETED)
            .withEnvironment(slot.environment)
            .withProject(slot.project)
            .with(EnvironmentsEvents.EVENT_SLOT_QUALIFIER, slot.qualifier)
            .build()

    override fun pipelineCreation(pipeline: SlotPipeline): Event =
        Event.of(EnvironmentsEvents.PIPELINE_CREATION)
            .withPipeline(pipeline)
            .build()

    override fun pipelineDeploying(pipeline: SlotPipeline): Event =
        Event.of(EnvironmentsEvents.PIPELINE_DEPLOYING)
            .withPipeline(pipeline)
            .build()

    override fun pipelineDeployed(pipeline: SlotPipeline): Event =
        Event.of(EnvironmentsEvents.PIPELINE_DEPLOYED)
            .withPipeline(pipeline)
            .build()

    override fun pipelineCancelled(pipeline: SlotPipeline): Event =
        Event.of(EnvironmentsEvents.PIPELINE_CANCELLED)
            .withPipeline(pipeline)
            .build()

    override fun pipelineFailed(pipeline: SlotPipeline): Event =
        Event.of(EnvironmentsEvents.PIPELINE_FAILED)
            .withPipeline(pipeline)
            .build()

    override fun pipelineStatusOverridden(pipeline: SlotPipeline): Event =
        Event.of(EnvironmentsEvents.PIPELINE_STATUS_OVERRIDDEN)
            .withPipeline(pipeline)
            .with(EnvironmentsEvents.EVENT_PIPELINE_OVERRIDING_USER, securityService.currentSignature.user.name)
            .build()

    override fun pipelineStatusChanged(pipeline: SlotPipeline): Event =
        Event.of(EnvironmentsEvents.PIPELINE_STATUS_CHANGED)
            .withPipeline(pipeline)
            .build()

    override fun getName(): String = "Registration of environment events"

    override fun startupOrder(): Int = StartupService.JOB_REGISTRATION

    override fun start() {
        eventFactory.register(EnvironmentsEvents.ENVIRONMENT_CREATION)
        eventFactory.register(EnvironmentsEvents.ENVIRONMENT_UPDATED)
        eventFactory.register(EnvironmentsEvents.ENVIRONMENT_DELETED)
        eventFactory.register(EnvironmentsEvents.SLOT_CREATION)
        eventFactory.register(EnvironmentsEvents.SLOT_UPDATED)
        eventFactory.register(EnvironmentsEvents.SLOT_DELETED)
        eventFactory.register(EnvironmentsEvents.PIPELINE_CREATION)
        eventFactory.register(EnvironmentsEvents.PIPELINE_DEPLOYING)
        eventFactory.register(EnvironmentsEvents.PIPELINE_DEPLOYED)
        eventFactory.register(EnvironmentsEvents.PIPELINE_CANCELLED)
        eventFactory.register(EnvironmentsEvents.PIPELINE_FAILED)
        eventFactory.register(EnvironmentsEvents.PIPELINE_STATUS_OVERRIDDEN)
        eventFactory.register(EnvironmentsEvents.PIPELINE_STATUS_CHANGED)
    }

}