package net.nemerosa.ontrack.extension.workflows.graphql

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordFilter
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordingService
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowEngine
import net.nemerosa.ontrack.extension.workflows.notifications.WorkflowNotificationChannel
import net.nemerosa.ontrack.extension.workflows.notifications.WorkflowNotificationChannelOutput
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.json.parseOrNull
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.toProjectEntityID
import org.springframework.stereotype.Component

/**
 * Contributes a `workflowInstances` field to all project entities, returning the workflows which
 * have been launched by a notification on an event targeting this entity.
 *
 * There is no direct link between an entity and a workflow instance: the link goes through the
 * notification records of the `workflow` channel, whose output carries the workflow instance ID.
 *
 * The lookup of those records is done as admin on purpose: reading notification records requires
 * the `NotificationRecordingAccess` global function, while a workflow instance is readable by any
 * authenticated user. Only the instance IDs escape the privileged block — the records themselves
 * are never returned by this field.
 */
@Component
class GQLProjectEntityWorkflowInstancesFieldContributor(
    private val gqlTypeWorkflowInstance: GQLTypeWorkflowInstance,
    private val notificationRecordingService: NotificationRecordingService,
    private val workflowEngine: WorkflowEngine,
    private val securityService: SecurityService,
) : GQLProjectEntityFieldContributor {

    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType,
    ): List<GraphQLFieldDefinition> = listOf(
        GraphQLFieldDefinition.newFieldDefinition()
            .name("workflowInstances")
            .description(
                "Workflows which have been launched by a notification on an event for this entity, " +
                        "most recent first. Resolved by scanning the $MAX_RECORDS most recent workflow " +
                        "notification records for this entity, so the list may be truncated on entities " +
                        "which accumulate many of them, like a project or a branch."
            )
            .type(listType(gqlTypeWorkflowInstance.typeRef))
            .dataFetcher { env ->
                val entity: ProjectEntity = env.getSource()!!
                getWorkflowInstances(entity)
            }
            .build()
    )

    private fun getWorkflowInstances(entity: ProjectEntity) =
        securityService.asAdmin {
            notificationRecordingService.filter(
                NotificationRecordFilter(
                    offset = 0,
                    size = MAX_RECORDS,
                    channel = WorkflowNotificationChannel.TYPE,
                    eventEntityId = entity.toProjectEntityID(),
                )
            ).pageItems.mapNotNull { record ->
                // Records outlive schema changes, so a record whose output cannot be parsed is skipped
                record.result.output?.parseOrNull<WorkflowNotificationChannelOutput>()?.workflowInstanceId
            }.distinct()
        }.let { instanceIds ->
            // Loaded in one batch: resolving them one by one would cost three queries and a
            // transaction each. Records outlive instances, so ids which no longer resolve are
            // skipped, and the record order (most recent first) is preserved.
            workflowEngine.findWorkflowInstances(instanceIds)
        }

    companion object {
        /**
         * Maximum number of notification records scanned for one entity.
         */
        const val MAX_RECORDS = 100
    }
}
