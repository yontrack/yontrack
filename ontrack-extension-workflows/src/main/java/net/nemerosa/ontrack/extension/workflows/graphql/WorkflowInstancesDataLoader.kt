package net.nemerosa.ontrack.extension.workflows.graphql

import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.extension.workflows.notifications.EntityWorkflowInstanceService
import net.nemerosa.ontrack.model.structure.ProjectEntityID
import org.springframework.graphql.execution.BatchLoaderRegistry
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * The batching behind the `workflowInstances` field of every project entity.
 *
 * The field is contributed to *every* project entity, so a page of them resolves it once per
 * source: a build screen showing five promotion runs used to pay five record queries, five
 * instance queries and five privileged `asAdmin` blocks. The batched lookup
 * ([EntityWorkflowInstanceService.findWorkflowInstancesByEntities]) already existed and was
 * already trusted by the delivery map; only the field was still asking one entity at a time.
 *
 * Registered as a **mapped** batch loader: the keys are entities and the values are their own
 * lists, so a per-key answer is what the loader has to produce. An entity the service answers
 * nothing for is filled in with an empty list rather than left out - a key missing from a mapped
 * batch loader's answer resolves to `null`, and the field has always answered `[]` for an entity
 * with no workflows.
 */
@Component
class WorkflowInstancesDataLoader(
    registry: BatchLoaderRegistry,
    private val entityWorkflowInstanceService: EntityWorkflowInstanceService,
) {

    init {
        registry.forName<ProjectEntityID, List<WorkflowInstance>>(NAME)
            /*
             * `Mono.fromCallable` and no scheduler: the loader is dispatched on the thread running
             * the GraphQL execution and the lookup is blocking, so staying on that thread is what
             * keeps the caller's security context - which the service's own `asAdmin` block is
             * entered from - the one the lookup runs under.
             */
            .registerMappedBatchLoader { keys, _ -> Mono.fromCallable { load(keys) } }
    }

    /**
     * The instances of each of the given [entities], every key present in the answer.
     */
    fun load(entities: Set<ProjectEntityID>): Map<ProjectEntityID, List<WorkflowInstance>> {
        val found = entityWorkflowInstanceService.findWorkflowInstancesByEntities(entities)
        return entities.associateWith { found[it] ?: emptyList() }
    }

    companion object {
        /**
         * The name the field's data fetcher asks for the loader under.
         */
        const val NAME = "workflowInstances"
    }

}
