package net.nemerosa.ontrack.extension.workflows.graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLTypeReference
import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityID
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * That the `workflowInstances` field resolves through the data loader, and not by calling the
 * single-entity lookup itself.
 *
 * The other half of what [WorkflowInstancesDataLoaderTest] pins: a field which went back to calling
 * the service directly would still answer correctly, and the only place that shows is here.
 */
class GQLProjectEntityWorkflowInstancesFieldContributorTest {

    private val gqlTypeWorkflowInstance = mockk<GQLTypeWorkflowInstance> {
        every { typeRef } returns GraphQLTypeReference("WorkflowInstance")
    }

    private val contributor = GQLProjectEntityWorkflowInstancesFieldContributor(gqlTypeWorkflowInstance)

    @Test
    fun `The field asks the data loader for the entity it is resolving`() {
        val entity = mockk<ProjectEntity> {
            every { projectEntityType } returns ProjectEntityType.PROMOTION_RUN
            every { id } returns ID.of(42)
            every { id() } returns 42
        }

        val instances = listOf(mockk<WorkflowInstance>())
        val loaded = mutableListOf<ProjectEntityID>()
        val dataLoader = mockk<DataLoader<ProjectEntityID, List<WorkflowInstance>>> {
            every { load(any()) } answers {
                loaded += firstArg<ProjectEntityID>()
                CompletableFuture.completedFuture(instances)
            }
        }
        val registry = mockk<DataLoaderRegistry> {
            every {
                getDataLoader<ProjectEntityID, List<WorkflowInstance>>(WorkflowInstancesDataLoader.NAME)
            } returns dataLoader
        }
        val env = mockk<DataFetchingEnvironment> {
            every { getSource<ProjectEntity>() } returns entity
            every { dataLoaderRegistry } returns registry
        }

        @Suppress("UNCHECKED_CAST")
        val result = contributor.dataFetcher.get(env) as CompletableFuture<List<WorkflowInstance>>

        assertEquals(listOf(ProjectEntityID(ProjectEntityType.PROMOTION_RUN, 42)), loaded)
        assertEquals(instances, result.get())
    }

    @Test
    fun `The field is contributed to every project entity`() {
        ProjectEntityType.values().forEach { type ->
            val field = contributor.getFields(ProjectEntity::class.java, type)
                .singleOrNull { it.name == "workflowInstances" }
            assertNotNull(field, "No workflowInstances field for ${'$'}type")
        }
    }

}
