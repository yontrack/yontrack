package net.nemerosa.ontrack.extension.workflows.engine

import net.nemerosa.ontrack.extension.workflows.AbstractWorkflowTestSupport
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowFixtures
import net.nemerosa.ontrack.extension.workflows.registry.WorkflowParser
import net.nemerosa.ontrack.extension.workflows.repository.WorkflowInstanceRepository
import net.nemerosa.ontrack.model.events.MockEventType
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.SerializableEventService
import net.nemerosa.ontrack.model.templating.TemplatingContextData
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertTrue

/**
 * Every lookup on `WKF_INSTANCE_NODES` and `WKF_INSTANCE_CONTEXT` filters on `INSTANCE_ID`, either
 * alone or together with the second key column. Both tables have a primary key whose leading column
 * is `INSTANCE_ID`, so the primary key serves all of them and no standalone index on `(INSTANCE_ID)`
 * is needed.
 *
 * These are the plans that must survive dropping the redundant indexes (see
 * `V81__1654_drop_redundant_workflow_node_indexes.sql`): each query shape is asserted to be
 * index-served, with sequential scans disabled so the plan reflects what is *usable* rather than
 * what happens to be cheapest at the current data volume.
 */
class WorkflowInstanceIndexUsageIT : AbstractWorkflowTestSupport() {

    @Autowired
    private lateinit var workflowInstanceRepository: WorkflowInstanceRepository

    @Autowired
    private lateinit var serializableEventService: SerializableEventService

    @BeforeEach
    fun instances() {
        repeat(20) {
            workflowInstanceRepository.createInstance(
                createInstance(
                    workflow = WorkflowParser.parseYamlWorkflow(WorkflowFixtures.simpleLinearWorkflowYaml)
                        .rename { uid("w-") },
                    event = serializableEventService.dehydrate(MockEventType.mockEvent(uid("t-"))),
                    contexts = mapOf(
                        "ctx" to TemplatingContextData(id = "mock", data = mapOf("value" to it).asJson()),
                    ),
                    triggerData = workflowTestSupport.testTriggerData(),
                )
            )
        }
        namedParameterJdbcTemplate.jdbcTemplate.execute("ANALYZE WKF_INSTANCE_NODES")
        namedParameterJdbcTemplate.jdbcTemplate.execute("ANALYZE WKF_INSTANCE_CONTEXT")
    }

    private fun explain(sql: String, params: Map<String, Any>): String {
        val jdbc = namedParameterJdbcTemplate
        jdbc.jdbcTemplate.execute("SET LOCAL enable_seqscan = off")
        return jdbc.query("EXPLAIN $sql", params) { rs, _ -> rs.getString(1) }.joinToString("\n")
    }

    /**
     * Asserts the query is served by [expectedIndex] specifically.
     *
     * Naming the index is the whole point: asserting merely that "some index" is used would pass
     * just as well with the redundant indexes still in place, since those are indexes too, and the
     * suite would silently stop guarding anything.
     */
    private fun assertServedBy(expectedIndex: String, sql: String, params: Map<String, Any>): String {
        val plan = explain(sql, params)
        assertTrue(
            plan.contains(expectedIndex, ignoreCase = true),
            "Expected $expectedIndex to serve the query, but the plan was:\n$plan",
        )
        assertTrue(
            !plan.contains("Seq Scan", ignoreCase = true),
            "Expected no sequential scan, but the plan was:\n$plan",
        )
        return plan
    }

    @Test
    fun `Reading the nodes of one instance`() {
        assertServedBy(
            NODES_PK,
            "SELECT * FROM WKF_INSTANCE_NODES WHERE INSTANCE_ID = :instanceId",
            mapOf("instanceId" to "some-instance"),
        )
    }

    @Test
    fun `Reading the nodes of several instances`() {
        assertServedBy(
            NODES_PK,
            "SELECT * FROM WKF_INSTANCE_NODES WHERE INSTANCE_ID IN (:ids)",
            mapOf("ids" to setOf("a", "b")),
        )
    }

    @Test
    fun `Reading or updating one node`() {
        val plan = assertServedBy(
            NODES_PK,
            "SELECT * FROM WKF_INSTANCE_NODES WHERE INSTANCE_ID = :instanceId AND NODE_ID = :nodeId",
            mapOf("instanceId" to "some-instance", "nodeId" to "some-node"),
        )
        // The primary key matches both columns, so NODE_ID is an index condition. While the
        // redundant one-column index existed the planner preferred it and had to re-check NODE_ID
        // as a post-scan filter instead.
        assertNodeIdIsAnIndexCondition(plan)
    }

    @Test
    fun `Reading the statuses of several nodes of one instance`() {
        val plan = assertServedBy(
            NODES_PK,
            "SELECT NODE_ID, STATUS FROM WKF_INSTANCE_NODES WHERE INSTANCE_ID = :instanceId AND NODE_ID IN (:nodeIds)",
            mapOf("instanceId" to "some-instance", "nodeIds" to setOf("a", "b")),
        )
        assertNodeIdIsAnIndexCondition(plan)
    }

    /**
     * Asserts NODE_ID is resolved by the index rather than re-checked afterwards.
     */
    private fun assertNodeIdIsAnIndexCondition(plan: String) {
        val indexCond = plan.lines().firstOrNull { it.contains("Index Cond") } ?: ""
        assertTrue(
            indexCond.contains("node_id", ignoreCase = true),
            "NODE_ID must be an index condition, but the plan was:\n$plan",
        )
    }

    @Test
    fun `Reading the contexts of one instance`() {
        assertServedBy(
            CONTEXT_PK,
            "SELECT * FROM WKF_INSTANCE_CONTEXT WHERE INSTANCE_ID = :instanceId",
            mapOf("instanceId" to "some-instance"),
        )
    }

    @Test
    fun `Reading the contexts of several instances`() {
        assertServedBy(
            CONTEXT_PK,
            "SELECT * FROM WKF_INSTANCE_CONTEXT WHERE INSTANCE_ID IN (:ids)",
            mapOf("ids" to setOf("a", "b")),
        )
    }

    @Test
    fun `Joining instances and their nodes`() {
        // The shape used by the instance filtering query (see findInstancesWithStatus)
        assertServedBy(
            NODES_PK,
            "SELECT i.id FROM WKF_INSTANCES i JOIN WKF_INSTANCE_NODES n ON i.id = n.instance_id GROUP BY i.id",
            emptyMap(),
        )
    }

    companion object {
        private const val NODES_PK = "wkf_instance_nodes_pk"
        private const val CONTEXT_PK = "wkf_instance_context_pk"
    }
}
