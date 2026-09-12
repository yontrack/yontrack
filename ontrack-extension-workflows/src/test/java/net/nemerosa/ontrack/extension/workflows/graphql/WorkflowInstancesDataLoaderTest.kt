package net.nemerosa.ontrack.extension.workflows.graphql

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstance
import net.nemerosa.ontrack.extension.workflows.notifications.EntityWorkflowInstanceService
import net.nemerosa.ontrack.model.structure.ProjectEntityID
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.junit.jupiter.api.Test
import org.springframework.graphql.execution.BatchLoaderRegistry
import kotlin.test.assertEquals

/**
 * The batching of the `workflowInstances` field.
 *
 * Batching which silently stops batching is invisible from the answer - the field returns exactly
 * the same JSON either way - so what is pinned here is the *call*: one batched lookup whatever the
 * number of entities, and never the single-entity one.
 */
class WorkflowInstancesDataLoaderTest {

    private val service = mockk<EntityWorkflowInstanceService>()

    /** The registration itself is Spring's; only the loading function is this class's own. */
    private val registry = mockk<BatchLoaderRegistry>(relaxed = true)

    private val loader = WorkflowInstancesDataLoader(registry, service)

    private fun run(id: Int) = ProjectEntityID(ProjectEntityType.PROMOTION_RUN, id)

    private fun instance(id: String) = mockk<WorkflowInstance> {
        every { this@mockk.id } returns id
    }

    @Test
    fun `Every entity is looked up in one batched call`() {
        val entities = setOf(run(1), run(2), run(3))
        every { service.findWorkflowInstancesByEntities(entities) } returns mapOf(
            run(1) to listOf(instance("i1")),
            run(2) to listOf(instance("i2a"), instance("i2b")),
        )

        val result = loader.load(entities)

        assertEquals(listOf("i1"), result.getValue(run(1)).map { it.id })
        assertEquals(listOf("i2a", "i2b"), result.getValue(run(2)).map { it.id })

        verify(exactly = 1) { service.findWorkflowInstancesByEntities(entities) }
        // The single-entity lookup is the N+1 this exists to remove: a loader which fell back to it
        // per key would answer identically and cost 2N queries.
        verify(exactly = 0) { service.findWorkflowInstancesByEntity(any()) }
    }

    @Test
    fun `An entity with no workflow answers an empty list rather than nothing`() {
        // A key missing from a mapped batch loader's answer resolves to `null`, and the field has
        // always answered `[]` - so the gap is filled here rather than at the schema's expense.
        val entities = setOf(run(1), run(2))
        every { service.findWorkflowInstancesByEntities(entities) } returns mapOf(
            run(1) to listOf(instance("i1")),
        )

        val result = loader.load(entities)

        assertEquals(entities, result.keys)
        assertEquals(emptyList(), result.getValue(run(2)))
    }

    @Test
    fun `No entity means no lookup at all`() {
        every { service.findWorkflowInstancesByEntities(emptySet()) } returns emptyMap()

        assertEquals(emptyMap(), loader.load(emptySet()))
    }

}
