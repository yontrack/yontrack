package net.nemerosa.ontrack.extension.workflows.engine

import net.nemerosa.ontrack.extension.workflows.AbstractWorkflowTestSupport
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowFixtures
import net.nemerosa.ontrack.extension.workflows.registry.WorkflowParser
import net.nemerosa.ontrack.extension.workflows.repository.WorkflowInstanceRepository
import net.nemerosa.ontrack.model.events.MockEventType
import net.nemerosa.ontrack.model.events.SerializableEventService
import net.nemerosa.ontrack.model.templating.TemplatingContextData
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Batched loading of workflow instances, used by the `workflowInstances` field on project entities
 * to avoid one query (and one transaction) per instance.
 */
class WorkflowInstanceBatchLoadingIT : AbstractWorkflowTestSupport() {

    @Autowired
    private lateinit var workflowInstanceRepository: WorkflowInstanceRepository

    @Autowired
    private lateinit var workflowEngine: WorkflowEngine

    @Autowired
    private lateinit var serializableEventService: SerializableEventService

    private fun newInstance(contexts: Map<String, TemplatingContextData> = emptyMap()): WorkflowInstance {
        val instance = createInstance(
            workflow = WorkflowParser.parseYamlWorkflow(WorkflowFixtures.simpleLinearWorkflowYaml)
                .rename { uid("w-") },
            event = serializableEventService.dehydrate(MockEventType.mockEvent(uid("t-"))),
            contexts = contexts,
            triggerData = workflowTestSupport.testTriggerData(),
        )
        workflowInstanceRepository.createInstance(instance)
        return instance
    }

    @Test
    fun `Loading several instances at once returns them all`() {
        val instances = (1..3).map { newInstance() }
        val loaded = workflowInstanceRepository.findWorkflowInstances(instances.map { it.id })
        assertEquals(
            instances.map { it.id }.toSet(),
            loaded.map { it.id }.toSet(),
        )
    }

    @Test
    fun `Batched loading returns exactly what one-by-one loading returns`() {
        // This is the property that matters: the batch is an optimisation, not a change of contract.
        val instances = (1..3).map {
            newInstance(
                contexts = mapOf(
                    "ctx-$it" to TemplatingContextData(id = "mock", data = mapOf("value" to it).asJson()),
                )
            )
        }
        val ids = instances.map { it.id }

        val oneByOne = ids.mapNotNull { workflowInstanceRepository.findWorkflowInstance(it) }
            .associateBy { it.id }
        val batched = workflowInstanceRepository.findWorkflowInstances(ids)
            .associateBy { it.id }

        assertEquals(oneByOne.keys, batched.keys)
        oneByOne.forEach { (id, expected) ->
            assertEquals(
                expected.truncateTimestamp(),
                assertNotNull(batched[id]).truncateTimestamp(),
                "Instance $id must load identically batched and one by one",
            )
        }
    }

    @Test
    fun `Batched loading carries the node executions of each instance`() {
        val instances = (1..2).map { newInstance() }
        val loaded = workflowInstanceRepository.findWorkflowInstances(instances.map { it.id })
            .associateBy { it.id }
        instances.forEach { instance ->
            val nodes = assertNotNull(loaded[instance.id]).nodesExecutions
            assertTrue(nodes.isNotEmpty(), "Nodes must be loaded for ${instance.id}")
            assertEquals(
                instance.nodesExecutions.map { it.id }.toSet(),
                nodes.map { it.id }.toSet(),
            )
        }
    }

    @Test
    fun `Batched loading does not mix the nodes of two instances`() {
        val first = newInstance()
        val second = newInstance()
        val loaded = workflowInstanceRepository.findWorkflowInstances(listOf(first.id, second.id))
            .associateBy { it.id }
        assertEquals(
            first.nodesExecutions.size,
            assertNotNull(loaded[first.id]).nodesExecutions.size,
        )
        assertEquals(
            second.nodesExecutions.size,
            assertNotNull(loaded[second.id]).nodesExecutions.size,
        )
    }

    @Test
    fun `Batched loading carries the contexts of each instance`() {
        val instance = newInstance(
            contexts = mapOf(
                "my-context" to TemplatingContextData(id = "mock", data = mapOf("text" to "value").asJson()),
            )
        )
        val other = newInstance()
        val loaded = workflowInstanceRepository.findWorkflowInstances(listOf(instance.id, other.id))
            .associateBy { it.id }
        assertEquals(
            setOf("my-context"),
            assertNotNull(loaded[instance.id]).contexts.keys,
        )
        assertEquals(
            emptySet(),
            assertNotNull(loaded[other.id]).contexts.keys,
        )
    }

    @Test
    fun `Unknown ids are skipped`() {
        val instance = newInstance()
        val loaded = workflowInstanceRepository.findWorkflowInstances(
            listOf(instance.id, uid("missing-"))
        )
        assertEquals(listOf(instance.id), loaded.map { it.id })
    }

    /**
     * The batch replaces N indexed single-row lookups with three `IN` queries, which is only a win
     * if those queries are index-served too. Both child tables have a primary key whose leading
     * column is INSTANCE_ID, so the prefix should serve them -- asserted here rather than assumed,
     * with sequential scans disabled so the plan reflects what is *usable* regardless of how much
     * data the test happens to have.
     */
    private fun explainInPlan(table: String): String {
        val jdbc = namedParameterJdbcTemplate
        jdbc.jdbcTemplate.execute("SET LOCAL enable_seqscan = off")
        return jdbc.query(
            "EXPLAIN SELECT * FROM $table WHERE INSTANCE_ID IN (:ids)",
            mapOf("ids" to setOf("a", "b")),
        ) { rs, _ -> rs.getString(1) }.joinToString("\n")
    }

    @Test
    fun `The batched node query is index-served`() {
        val plan = explainInPlan("WKF_INSTANCE_NODES")
        assertTrue(
            plan.contains("Index", ignoreCase = true) && !plan.contains("Seq Scan", ignoreCase = true),
            "Expected an index-based plan, got:\n$plan",
        )
    }

    @Test
    fun `The batched context query is index-served`() {
        val plan = explainInPlan("WKF_INSTANCE_CONTEXT")
        assertTrue(
            plan.contains("Index", ignoreCase = true) && !plan.contains("Seq Scan", ignoreCase = true),
            "Expected an index-based plan, got:\n$plan",
        )
    }

    @Test
    fun `Loading no id runs no query and returns nothing`() {
        assertEquals(emptyList(), workflowInstanceRepository.findWorkflowInstances(emptyList()))
    }

    /**
     * Starts an instance through the engine, so that it is committed: the engine loads in a new
     * transaction, which cannot see rows written by the test's own transaction.
     */
    private fun startedInstanceId(): String =
        workflowTestSupport.registerLaunchAndWaitForWorkflow(
            """
                name: ${uid("w-")}
                nodes:
                    - id: start
                      executorId: mock
                      data:
                        text: Start
            """.trimIndent()
        )

    @Test
    fun `The engine exposes the batched loading`() {
        val ids = (1..2).map { startedInstanceId() }
        val loaded = workflowEngine.findWorkflowInstances(ids)
        assertEquals(ids.toSet(), loaded.map { it.id }.toSet())
    }

    @Test
    fun `The engine keeps the order of the requested ids`() {
        // The field contributor relies on this: records come back most recent first, and that
        // order must survive the batch.
        val ids = (1..3).map { startedInstanceId() }.reversed()
        val loaded = workflowEngine.findWorkflowInstances(ids)
        assertEquals(ids, loaded.map { it.id })
    }

    @Test
    fun `The engine skips ids which no longer resolve, keeping the order of the rest`() {
        val first = startedInstanceId()
        val second = startedInstanceId()
        val loaded = workflowEngine.findWorkflowInstances(
            listOf(first, uid("missing-"), second)
        )
        assertEquals(listOf(first, second), loaded.map { it.id })
    }
}
