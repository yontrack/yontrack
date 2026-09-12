package net.nemerosa.ontrack.extension.workflows.graphql

import graphql.schema.DataFetcher
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.extension.workflows.notifications.EntityWorkflowInstanceService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityID
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.toProjectEntityID
import org.dataloader.DataLoader
import org.springframework.stereotype.Component

/**
 * Contributes a `workflowInstances` field to all project entities, returning the workflows which
 * have been launched by a notification on an event targeting this entity.
 *
 * How that link is made, and what it costs, belongs to [EntityWorkflowInstanceService]: the delivery
 * map reads the same thing for a whole branch at once (#1711), and the two must not answer it
 * differently.
 *
 * The field is resolved through a **data loader** ([WorkflowInstancesDataLoader]) rather than
 * directly: it is contributed to every project entity, so a page of them - the five promotion runs
 * of a build, say - would otherwise cost a record query, an instance query and a privileged
 * `asAdmin` block *each*. Narrowing the field selection does not help, because the cost is per
 * entity resolved and not per field asked for.
 */
@Component
class GQLProjectEntityWorkflowInstancesFieldContributor(
    private val gqlTypeWorkflowInstance: GQLTypeWorkflowInstance,
) : GQLProjectEntityFieldContributor {

    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType,
    ): List<GraphQLFieldDefinition> = listOf(
        GraphQLFieldDefinition.newFieldDefinition()
            .name("workflowInstances")
            .description(
                "Workflows which have been launched by a notification on an event for this entity, " +
                        "most recent first. Resolved by scanning the " +
                        "${EntityWorkflowInstanceService.MAX_RECORDS} most recent workflow " +
                        "notification records for this entity, so the list may be truncated on entities " +
                        "which accumulate many of them, like a project or a branch."
            )
            .type(listType(gqlTypeWorkflowInstance.typeRef))
            .dataFetcher(dataFetcher)
            .build()
    )

    /**
     * The field's own resolution, named rather than inlined into the builder above.
     *
     * `GraphQLFieldDefinition` does not hand a data fetcher back, so this is the only seam a test
     * can reach: without it, a field which went back to calling the single-entity lookup itself
     * would answer identically and no test would notice.
     */
    internal val dataFetcher = DataFetcher { env ->
        val entity: ProjectEntity = env.getSource()!!
        val loader: DataLoader<ProjectEntityID, List<WorkflowInstance>> =
            env.dataLoaderRegistry.getDataLoader(WorkflowInstancesDataLoader.NAME)
                ?: error("No ${WorkflowInstancesDataLoader.NAME} data loader is registered.")
        loader.load(entity.toProjectEntityID())
    }

}
