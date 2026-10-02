package net.nemerosa.ontrack.extension.environments.events

import net.nemerosa.ontrack.model.events.*

object EnvironmentsEvents {

    const val EVENT_ENVIRONMENT_ID = "ENVIRONMENT_ID"
    const val EVENT_ENVIRONMENT_NAME = "ENVIRONMENT_NAME"

    const val EVENT_SLOT_ID = "SLOT_ID"
    const val EVENT_SLOT_QUALIFIER = "SLOT_QUALIFIER"

    const val EVENT_PIPELINE_ID = "PIPELINE_ID"
    const val EVENT_PIPELINE_OVERRIDING_USER = "PIPELINE_OVERRIDING_USER"
    const val EVENT_PIPELINE_NUMBER = "PIPELINE_NUMBER"
    const val EVENT_PIPELINE_STATUS = "PIPELINE_STATUS"

    const val EVENT_SLOT_WORKFLOW_ID = "SLOT_WORKFLOW_ID"
    const val EVENT_SLOT_WORKFLOW_INSTANCE_ID = "SLOT_WORKFLOW_INSTANCE_ID"
    const val EVENT_WORKFLOW_NAME = "WORKFLOW_NAME"
    const val EVENT_OVERRIDE_MESSAGE = "OVERRIDE_MESSAGE"

    private val eventEnvironmentContext = eventContext(
        eventValue(EVENT_ENVIRONMENT_ID, "ID of the environment"),
        eventValue(EVENT_ENVIRONMENT_NAME, "Name of the environment"),
    )

    private val eventSlotContext = eventEnvironmentContext.add(
        eventProject("Project for the slot"),
        eventValue(EVENT_SLOT_ID, "ID of the slot"),
        eventValue(EVENT_SLOT_QUALIFIER, "Qualifier of the slot"),
    )

    private val eventPipelineContext = eventSlotContext.add(
        eventValue(EVENT_PIPELINE_ID, "ID of the pipeline"),
        eventProject("Project of the build in the pipeline"),
        eventBranch("Branch of the build in the pipeline"),
        eventBuild("Build in the pipeline"),
    )

    val ENVIRONMENT_CREATION: EventType = SimpleEventType(
        id = "environment-creation",
        template = """
            Environment ${'$'}{$EVENT_ENVIRONMENT_NAME} has been created.
        """.trimIndent(),
        description = "When an environment is created",
        context = eventEnvironmentContext
    )

    val ENVIRONMENT_UPDATED: EventType = SimpleEventType(
        id = "environment-updated",
        template = """
            Environment ${'$'}{$EVENT_ENVIRONMENT_NAME} has been updated.
        """.trimIndent(),
        description = "When an environment is updated",
        context = eventEnvironmentContext
    )

    val ENVIRONMENT_DELETED: EventType = SimpleEventType(
        id = "environment-deleted",
        template = """
            Environment ${'$'}{$EVENT_ENVIRONMENT_NAME} has been deleted.
        """.trimIndent(),
        description = "When an environment is deleted",
        context = eventContext(
            eventValue(EVENT_ENVIRONMENT_NAME, "Name of the environment"),
        )
    )

    val SLOT_CREATION: EventType = SimpleEventType(
        id = "slot-creation",
        template = """
            Slot ${'$'}{#.slot} for environment ${'$'}{$EVENT_ENVIRONMENT_NAME} has been created.
        """.trimIndent(),
        description = "When a slot is created",
        context = eventSlotContext
    )

    val SLOT_UPDATED: EventType = SimpleEventType(
        id = "slot-updated",
        template = """
            Slot ${'$'}{#.slot} for environment ${'$'}{$EVENT_ENVIRONMENT_NAME} has been updated.
        """.trimIndent(),
        description = "When a slot is updated",
        context = eventSlotContext
    )

    val SLOT_DELETED: EventType = SimpleEventType(
        id = "slot-deleted",
        template = """
            Slot ${'$'}{project} (qualifier = "${'$'}{$EVENT_SLOT_QUALIFIER}") for environment ${'$'}{$EVENT_ENVIRONMENT_NAME} has been deleted.
        """.trimIndent(),
        description = "When a slot is updated",
        context = eventSlotContext
    )

    val PIPELINE_CREATION: EventType = SimpleEventType(
        id = "slot-pipeline-creation",
        template = """
            Pipeline ${'$'}{#.pipeline} has started.
        """.trimIndent(),
        description = "When a slot pipeline has started",
        context = eventPipelineContext
    )

    val PIPELINE_DEPLOYING: EventType = SimpleEventType(
        id = "slot-pipeline-deploying",
        template = """
            Pipeline ${'$'}{#.pipeline} is starting its deployment.
        """.trimIndent(),
        description = "When a slot pipeline is starting its deployment",
        context = eventPipelineContext
    )

    val PIPELINE_DEPLOYED: EventType = SimpleEventType(
        id = "slot-pipeline-deployed",
        template = """
            Pipeline ${'$'}{#.pipeline} has been deployed.
        """.trimIndent(),
        description = "When a slot pipeline has completed its deployment",
        context = eventPipelineContext
    )

    val PIPELINE_CANCELLED: EventType = SimpleEventType(
        id = "slot-pipeline-cancelled",
        template = """
            Pipeline ${'$'}{#.pipeline} has been cancelled.
        """.trimIndent(),
        description = "When a slot pipeline is cancelled",
        context = eventPipelineContext
    )

    val PIPELINE_FAILED: EventType = SimpleEventType(
        id = "slot-pipeline-failed",
        template = """
            Pipeline ${'$'}{#.pipeline} has failed.
        """.trimIndent(),
        description = "When a running slot pipeline is marked as failed",
        context = eventPipelineContext
    )

    val PIPELINE_STATUS_CHANGED: EventType = SimpleEventType(
        id = "slot-pipeline-status-changed",
        template = """
            Pipeline ${'$'}{#.pipeline} status has been updated.
        """.trimIndent(),
        description = "When a slot pipeline status is updated",
        context = eventPipelineContext
    )

    val PIPELINE_STATUS_OVERRIDDEN: EventType = SimpleEventType(
        id = "slot-pipeline-status-overridden",
        template = """
            Pipeline ${'$'}{#.pipeline} status has been overridden by ${'$'}{$EVENT_PIPELINE_OVERRIDING_USER}.
        """.trimIndent(),
        description = "When a slot pipeline status is updated",
        context = eventPipelineContext.add(
            eventValue(EVENT_PIPELINE_OVERRIDING_USER, "User who has overridden the pipeline status")
        )
    )

    /**
     * The pipeline is gone: the event does not render it as a link, and carries what identifies it.
     */
    val PIPELINE_DELETED: EventType = SimpleEventType(
        id = "slot-pipeline-deleted",
        template = """
            Pipeline #${'$'}{$EVENT_PIPELINE_NUMBER} of build ${'$'}{build} for environment ${'$'}{$EVENT_ENVIRONMENT_NAME} has been deleted.
        """.trimIndent(),
        description = "When a slot pipeline is deleted",
        context = eventPipelineContext.add(
            eventValue(EVENT_SLOT_QUALIFIER, "Qualifier of the slot"),
            eventValue(EVENT_PIPELINE_NUMBER, "Number of the deleted pipeline in its slot"),
            eventValue(EVENT_PIPELINE_STATUS, "Status of the pipeline when it was deleted"),
        )
    )

    val PIPELINE_WORKFLOW_OVERRIDDEN: EventType = SimpleEventType(
        id = "slot-pipeline-workflow-overridden",
        template = """
            Workflow ${'$'}{$EVENT_WORKFLOW_NAME} of pipeline ${'$'}{#.pipeline} has been overridden by ${'$'}{$EVENT_PIPELINE_OVERRIDING_USER}.
        """.trimIndent(),
        description = "When the result of a slot workflow is overridden for a slot pipeline",
        context = eventPipelineContext.add(
            eventValue(EVENT_SLOT_WORKFLOW_ID, "ID of the slot workflow"),
            eventValue(EVENT_SLOT_WORKFLOW_INSTANCE_ID, "ID of the overridden slot workflow instance"),
            eventValue(EVENT_WORKFLOW_NAME, "Name of the overridden workflow"),
            eventValue(EVENT_PIPELINE_OVERRIDING_USER, "User who has overridden the workflow"),
            eventValue(EVENT_OVERRIDE_MESSAGE, "Message given for the override"),
        )
    )

}
