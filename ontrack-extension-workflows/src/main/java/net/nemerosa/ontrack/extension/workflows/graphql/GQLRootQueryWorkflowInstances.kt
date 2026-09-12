package net.nemerosa.ontrack.extension.workflows.graphql

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.workflows.acl.WorkflowAudit
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceFilter
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceStatus
import net.nemerosa.ontrack.extension.workflows.repository.WorkflowInstanceRepository
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.enumArgument
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.graphql.support.stringArgument
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.stereotype.Component

@Component
class GQLRootQueryWorkflowInstances(
    private val gqlPaginatedListFactory: GQLPaginatedListFactory,
    private val gqlTypeWorkflowInstance: GQLTypeWorkflowInstance,
    private val workflowInstanceRepository: WorkflowInstanceRepository,
    private val securityService: SecurityService,
) : GQLRootQuery {
    override fun getFieldDefinition(): GraphQLFieldDefinition =
        gqlPaginatedListFactory.createRootPaginatedField<WorkflowInstance>(
            cache = GQLTypeCache(),
            fieldName = "workflowInstances",
            fieldDescription = "List of workflow instances",
            itemType = gqlTypeWorkflowInstance.typeName,
            arguments = listOf(
                stringArgument(ARG_ID, "ID of a workflow"),
                stringArgument(ARG_NAME, "Name of a workflow"),
                enumArgument<WorkflowInstanceStatus>(ARG_STATUS, "Status of the workflow"),
                stringArgument(ARG_TRIGGER_ID, "ID of the trigger of the workflow"),
                stringArgument(ARG_TRIGGER_DATA, "Text to find in the trigger data of the workflow"),
            ),
            itemPaginatedListProvider = { env, offset, size ->
                // Admin-only, deliberately. This query filters `triggerData` by free text into the
                // trigger JSON, and a slot workflow's trigger data is keyed by pipeline id, so
                // instances would otherwise be enumerable by pipeline id - routing around the slot
                // access check every other slot-side read honours. Per-row `ProjectView` filtering
                // is not an option here: `GQLPaginatedListFactory` computes the total *before* any
                // post-hoc filter, so filtering after paging gives wrong counts and short pages.
                //
                // `WorkflowAudit` already gates the only UI which consumes this query (the
                // *Workflows audit* page), so nothing user-facing is taken away.
                securityService.checkGlobalFunction(WorkflowAudit::class.java)
                val id: String? = env.getArgument(ARG_ID)
                val name: String? = env.getArgument(ARG_NAME)
                val status: WorkflowInstanceStatus? = env.getArgument<String?>(ARG_STATUS)?.let {
                    WorkflowInstanceStatus.valueOf(it)
                }
                val triggerId: String? = env.getArgument(ARG_TRIGGER_ID)
                val triggerData: String? = env.getArgument(ARG_TRIGGER_DATA)
                workflowInstanceRepository.findInstances(
                    WorkflowInstanceFilter(
                        offset = offset,
                        size = size,
                        id = id,
                        name = name,
                        status = status,
                        triggerId = triggerId,
                        triggerData = triggerData,
                    )
                )
            }
        )

    companion object {
        private const val ARG_NAME = "name"
        private const val ARG_STATUS = "status"
        private const val ARG_ID = "id"
        private const val ARG_TRIGGER_ID = "triggerId"
        private const val ARG_TRIGGER_DATA = "triggerData"
    }
}