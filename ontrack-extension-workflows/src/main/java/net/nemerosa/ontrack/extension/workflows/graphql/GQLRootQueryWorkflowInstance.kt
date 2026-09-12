package net.nemerosa.ontrack.extension.workflows.graphql

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.workflows.acl.WorkflowInstanceAccessService
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowEngine
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.support.stringArgument
import org.springframework.stereotype.Component

@Component
class GQLRootQueryWorkflowInstance(
    private val gqlTypeWorkflowInstance: GQLTypeWorkflowInstance,
    private val workflowEngine: WorkflowEngine,
    private val workflowInstanceAccessService: WorkflowInstanceAccessService,
) : GQLRootQuery {
    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("workflowInstance")
            .description("Gets an existing workflow instance using its ID")
            .type(gqlTypeWorkflowInstance.typeRef)
            .argument(stringArgument("id", "ID of the workflow instance", nullable = false))
            .dataFetcher { env ->
                val id: String = env.getArgument("id")!!
                workflowEngine.findWorkflowInstance(id)
                    // A denial answers `null`, exactly like a missing instance: throwing here would
                    // tell the caller that an instance they may not read exists.
                    ?.takeIf { workflowInstanceAccessService.isWorkflowInstanceAccessible(it) }
            }
            .build()
}
