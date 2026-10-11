package net.nemerosa.ontrack.extension.workflows.recovery

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.workflows.AbstractWorkflowTestSupport
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowEngine
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceFixtures
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceLauncher
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceNodeStatus
import net.nemerosa.ontrack.extension.workflows.engine.WorkflowInstanceStatus
import net.nemerosa.ontrack.extension.workflows.engine.getWorkflowInstance
import net.nemerosa.ontrack.extension.workflows.mock.MockWorkflowNodeExecutor
import net.nemerosa.ontrack.extension.workflows.repository.WorkflowInstanceRepository
import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Recovery of the workflow nodes left unfinished by a stopped Yontrack process (#2064).
 *
 * The stopped process is simulated by a process row whose heartbeat is old, or by no row at all.
 */
class WorkflowNodeRecoveryIT : AbstractWorkflowTestSupport() {

    @Autowired
    private lateinit var workflowNodeRecoveryService: WorkflowNodeRecoveryService

    @Autowired
    private lateinit var workflowInstanceRepository: WorkflowInstanceRepository

    @Autowired
    private lateinit var workflowEngine: WorkflowEngine

    @Autowired
    private lateinit var mockWorkflowNodeExecutor: MockWorkflowNodeExecutor

    @Autowired
    private lateinit var platformTransactionManager: PlatformTransactionManager

    private val system = WorkflowInstanceLauncher(account = null, actor = Actor.system(reason = null))

    /**
     * The test methods run in a transaction, which the engine, working in its own transactions,
     * would not see: the setup is committed.
     */
    private fun <T> committed(code: () -> T): T =
        TransactionTemplate(platformTransactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }.execute { code() }!!

    private fun process(heartbeat: LocalDateTime): String {
        val id = uid("p-")
        committed {
            namedParameterJdbcTemplate.update(
                """
                    INSERT INTO WKF_ENGINE_PROCESSES(ID, STARTED, HEARTBEAT)
                    VALUES (:id, :started, :heartbeat)
                """.trimIndent(),
                mapOf(
                    "id" to id,
                    "started" to Time.store(heartbeat.minusHours(1)),
                    "heartbeat" to Time.store(heartbeat),
                )
            )
        }
        return id
    }

    private fun deadProcess() = process(heartbeat = Time.now.minusHours(1))

    private fun liveProcess() = process(heartbeat = Time.now)

    private data class NodeState(
        val status: WorkflowInstanceNodeStatus,
        val owner: String? = null,
        val dispatched: LocalDateTime? = Time.now,
    )

    /**
     * Stores a `start` -> `end` instance whose nodes are in the given states.
     */
    private fun storedInstance(
        start: NodeState,
        end: NodeState,
        launcher: WorkflowInstanceLauncher? = system,
        timestamp: LocalDateTime = Time.now,
    ): String {
        val instance = WorkflowInstanceFixtures.simpleLinear(timestamp = timestamp)
        committed {
            workflowInstanceRepository.createInstance(instance, launcher)
            mapOf("start" to start, "end" to end).forEach { (nodeId, state) ->
                namedParameterJdbcTemplate.update(
                    """
                        UPDATE WKF_INSTANCE_NODES
                        SET STATUS = :status, OWNER = :owner, DISPATCHED = :dispatched
                        WHERE INSTANCE_ID = :instanceId AND NODE_ID = :nodeId
                    """.trimIndent(),
                    mapOf(
                        "instanceId" to instance.id,
                        "nodeId" to nodeId,
                        "status" to state.status.name,
                        "owner" to state.owner,
                        "dispatched" to state.dispatched?.let { Time.store(it) },
                    )
                )
            }
        }
        return instance.id
    }

    private fun recover(processStarted: LocalDateTime = Time.now.minusHours(1)) {
        workflowNodeRecoveryService.recover(now = Time.now, processStarted = processStarted)
    }

    private fun owner(instanceId: String, nodeId: String): String? =
        namedParameterJdbcTemplate.queryForObject(
            "SELECT OWNER FROM WKF_INSTANCE_NODES WHERE INSTANCE_ID = :instanceId AND NODE_ID = :nodeId",
            mapOf("instanceId" to instanceId, "nodeId" to nodeId),
            String::class.java
        )

    private fun assertCompleted(instanceId: String) {
        workflowTestSupport.waitForWorkflowInstance(instanceId)
        val instance = workflowEngine.getWorkflowInstance(instanceId)
        assertEquals(WorkflowInstanceStatus.SUCCESS, instance.status)
        assertEquals(2, mockWorkflowNodeExecutor.getTextsByInstanceId(instanceId).size)
    }

    /**
     * Both nodes are abandoned: the first one recovered is put in error, which stops the instance
     * and cancels the other one.
     */
    private fun assertAbandoned(instanceId: String, message: String) {
        workflowTestSupport.waitForWorkflowInstance(instanceId)
        val instance = workflowEngine.getWorkflowInstance(instanceId)
        assertEquals(WorkflowInstanceStatus.ERROR, instance.status)
        assertEquals(
            listOf(WorkflowInstanceNodeStatus.CANCELLED, WorkflowInstanceNodeStatus.ERROR),
            instance.nodesExecutions.map { it.status }.sorted()
        )
        assertEquals(message, instance.nodesExecutions.single { it.status == WorkflowInstanceNodeStatus.ERROR }.error)
        assertEquals(emptyList(), mockWorkflowNodeExecutor.getTextsByInstanceId(instanceId))
    }

    @Test
    fun `Waiting nodes of a stopped process are resumed and the workflow completes`() {
        val dead = deadProcess()
        val instanceId = storedInstance(
            start = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = dead),
            end = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = dead),
        )
        recover()
        assertCompleted(instanceId)
    }

    @Test
    fun `Waiting nodes of a process which left no trace are resumed`() {
        val instanceId = storedInstance(
            start = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = uid("gone-")),
            end = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = uid("gone-")),
        )
        recover()
        assertCompleted(instanceId)
    }

    @Test
    fun `A started node of a stopped process ends in error and stops its instance`() {
        val dead = deadProcess()
        val instanceId = storedInstance(
            start = NodeState(WorkflowInstanceNodeStatus.STARTED, owner = dead),
            end = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = dead),
        )
        recover()
        workflowTestSupport.waitForWorkflowInstance(instanceId)
        val instance = workflowEngine.getWorkflowInstance(instanceId)
        assertEquals(WorkflowInstanceStatus.ERROR, instance.status)
        assertEquals(WorkflowInstanceNodeStatus.ERROR, instance.getNode("start").status)
        assertEquals(WorkflowNodeRecoveryPolicy.INTERRUPTED, instance.getNode("start").error)
        assertEquals(WorkflowInstanceNodeStatus.CANCELLED, instance.getNode("end").status)
        assertEquals(emptyList(), mockWorkflowNodeExecutor.getTextsByInstanceId(instanceId))
    }

    @Test
    fun `Nodes of a live process are left alone`() {
        val live = liveProcess()
        val instanceId = storedInstance(
            start = NodeState(WorkflowInstanceNodeStatus.STARTED, owner = live),
            end = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = live),
        )
        recover()
        val instance = workflowEngine.getWorkflowInstance(instanceId)
        assertEquals(WorkflowInstanceNodeStatus.STARTED, instance.getNode("start").status)
        assertEquals(WorkflowInstanceNodeStatus.WAITING, instance.getNode("end").status)
        assertEquals(live, owner(instanceId, "start"))
        assertEquals(live, owner(instanceId, "end"))
    }

    @Test
    fun `Waiting nodes without owner are resumed once the recovering process is old enough`() {
        // Claimed by a version of Yontrack which did not record the owner of a node
        val instanceId = storedInstance(
            start = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = null),
            end = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = null),
        )
        recover(processStarted = Time.now.minusHours(1))
        assertCompleted(instanceId)
    }

    @Test
    fun `Created nodes whose message was lost are dispatched again`() {
        val instanceId = storedInstance(
            start = NodeState(WorkflowInstanceNodeStatus.CREATED, dispatched = Time.now.minusHours(1)),
            end = NodeState(WorkflowInstanceNodeStatus.CREATED, dispatched = Time.now.minusHours(1)),
        )
        recover()
        assertCompleted(instanceId)
    }

    @Test
    fun `Created nodes recently dispatched are left alone`() {
        val instanceId = storedInstance(
            start = NodeState(WorkflowInstanceNodeStatus.CREATED, dispatched = Time.now),
            end = NodeState(WorkflowInstanceNodeStatus.CREATED, dispatched = Time.now),
        )
        recover()
        val instance = workflowEngine.getWorkflowInstance(instanceId)
        assertEquals(WorkflowInstanceNodeStatus.CREATED, instance.getNode("start").status)
        assertEquals(WorkflowInstanceNodeStatus.CREATED, instance.getNode("end").status)
    }

    @Test
    fun `Abandoned nodes of an old instance are put in error`() {
        val dead = deadProcess()
        val instanceId = storedInstance(
            start = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = dead),
            end = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = dead),
            timestamp = Time.now.minusDays(2),
        )
        recover()
        assertAbandoned(instanceId, WorkflowNodeRecoveryPolicy.TOO_OLD)
    }

    @Test
    fun `Abandoned nodes whose launcher is unknown are put in error`() {
        val dead = deadProcess()
        val instanceId = storedInstance(
            start = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = dead),
            end = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = dead),
            launcher = null,
        )
        recover()
        assertAbandoned(instanceId, WorkflowNodeRecoveryPolicy.UNKNOWN_LAUNCHER)
    }

    @Test
    fun `Abandoned nodes are resumed as the account which launched the workflow`() {
        val account = asAdmin { doCreateAccount() }
        val dead = deadProcess()
        val instanceId = storedInstance(
            start = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = dead),
            end = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = dead),
            launcher = WorkflowInstanceLauncher(account = account.email, actor = null),
        )
        recover()
        assertCompleted(instanceId)
    }

    @Test
    fun `Abandoned nodes whose launcher account is gone are put in error`() {
        val dead = deadProcess()
        val instanceId = storedInstance(
            start = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = dead),
            end = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = dead),
            launcher = WorkflowInstanceLauncher(account = "${uid("gone-")}@test.com", actor = null),
        )
        recover()
        assertAbandoned(instanceId, WorkflowNodeRecoveryPolicy.LAUNCHER_GONE)
    }

    @Test
    fun `Concurrent recoveries resume a node once`() {
        val dead = deadProcess()
        val instanceId = storedInstance(
            start = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = dead),
            end = NodeState(WorkflowInstanceNodeStatus.WAITING, owner = dead),
        )
        val executor = Executors.newFixedThreadPool(4)
        try {
            val go = CountDownLatch(1)
            val futures = (1..4).map {
                executor.submit {
                    go.await()
                    recover()
                }
            }
            go.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        assertCompleted(instanceId)
    }

    @Test
    fun `Processes dead for long are forgotten`() {
        val forgotten = process(heartbeat = Time.now.minusDays(2))
        recover()
        assertNull(
            namedParameterJdbcTemplate.query(
                "SELECT ID FROM WKF_ENGINE_PROCESSES WHERE ID = :id",
                mapOf("id" to forgotten)
            ) { rs, _ -> rs.getString("ID") }.firstOrNull()
        )
    }
}
