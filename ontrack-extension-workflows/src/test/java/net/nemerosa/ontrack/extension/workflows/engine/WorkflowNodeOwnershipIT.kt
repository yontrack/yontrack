package net.nemerosa.ontrack.extension.workflows.engine

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.workflows.AbstractWorkflowTestSupport
import net.nemerosa.ontrack.extension.workflows.definition.WorkflowFixtures
import net.nemerosa.ontrack.extension.workflows.mock.MockWorkflowNodeExecutor
import net.nemerosa.ontrack.extension.workflows.registry.WorkflowParser
import net.nemerosa.ontrack.extension.workflows.repository.WorkflowInstanceRepository
import net.nemerosa.ontrack.it.waitUntil
import net.nemerosa.ontrack.model.events.MockEventType
import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * A node belongs to the process which claimed it, so that it can be recovered when this process
 * stops, and so that a process which lost it can no longer change it (#2064).
 */
@OptIn(ExperimentalTime::class)
class WorkflowNodeOwnershipIT : AbstractWorkflowTestSupport() {

    @Autowired
    private lateinit var workflowEngine: WorkflowEngine

    @Autowired
    private lateinit var workflowEngineProcess: WorkflowEngineProcess

    @Autowired
    private lateinit var workflowInstanceRepository: WorkflowInstanceRepository

    @Autowired
    private lateinit var mockWorkflowNodeExecutor: MockWorkflowNodeExecutor

    @Autowired
    private lateinit var platformTransactionManager: PlatformTransactionManager

    private fun <T> committed(code: () -> T): T =
        TransactionTemplate(platformTransactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }.execute { code() }!!

    private fun nodeColumn(instanceId: String, nodeId: String, column: String): String? =
        namedParameterJdbcTemplate.query(
            "SELECT $column FROM WKF_INSTANCE_NODES WHERE INSTANCE_ID = :instanceId AND NODE_ID = :nodeId",
            mapOf("instanceId" to instanceId, "nodeId" to nodeId),
        ) { rs, _ -> rs.getString(column) }.first()

    private fun instanceColumn(instanceId: String, column: String): String? =
        namedParameterJdbcTemplate.query(
            "SELECT $column FROM WKF_INSTANCES WHERE ID = :id",
            mapOf("id" to instanceId),
        ) { rs, _ -> rs.getString(column) }.first()

    private fun startSimpleLinear(): WorkflowInstance =
        workflowEngine.startWorkflow(
            workflow = WorkflowParser.parseYamlWorkflow(WorkflowFixtures.simpleLinearWorkflowYaml),
            event = MockEventType.serializedMockEvent("Ownership"),
            triggerData = testTriggerData(),
        )

    @Test
    fun `A node is owned by the process which ran it`() {
        val instance = asAdmin { startSimpleLinear() }
        workflowTestSupport.waitForWorkflowInstance(instance.id)
        assertEquals(workflowEngineProcess.id, nodeColumn(instance.id, "start", "OWNER"))
        assertEquals(workflowEngineProcess.id, nodeColumn(instance.id, "end", "OWNER"))
    }

    @Test
    fun `The account which launched an instance is recorded`() {
        val account = asAdmin { doCreateAccount() }
        val instance = asFixedAccount(account) { startSimpleLinear() }
        workflowTestSupport.waitForWorkflowInstance(instance.id)
        assertEquals(account.email, instanceColumn(instance.id, "ACCOUNT"))
        assertNotNull(instanceColumn(instance.id, "ACTOR"))
    }

    @Test
    fun `The system launching an instance is recorded as such`() {
        val instance = securityService.asAdmin { startSimpleLinear() }
        workflowTestSupport.waitForWorkflowInstance(instance.id)
        assertNull(instanceColumn(instance.id, "ACCOUNT"))
        assertNotNull(instanceColumn(instance.id, "ACTOR"))
    }

    @Test
    fun `A node delivered several times at once runs once`() {
        // `end` belongs to a live process, so that only `start` runs
        val live = uid("p-")
        val instance = WorkflowInstanceFixtures.simpleLinear()
        committed {
            namedParameterJdbcTemplate.update(
                "INSERT INTO WKF_ENGINE_PROCESSES(ID, STARTED, HEARTBEAT) VALUES (:id, :now, :now)",
                mapOf("id" to live, "now" to Time.store(Time.now.plusHours(1)))
            )
            workflowInstanceRepository.createInstance(
                instance,
                WorkflowInstanceLauncher(account = null, actor = Actor.system(reason = null))
            )
            namedParameterJdbcTemplate.update(
                "UPDATE WKF_INSTANCE_NODES SET STATUS = 'WAITING', OWNER = :owner WHERE INSTANCE_ID = :id AND NODE_ID = 'end'",
                mapOf("id" to instance.id, "owner" to live)
            )
        }

        val executor = Executors.newFixedThreadPool(4)
        try {
            val go = CountDownLatch(1)
            val futures = (1..4).map {
                executor.submit {
                    go.await()
                    securityService.asAdmin {
                        workflowEngine.processNode(instance.id, "start")
                    }
                }
            }
            go.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        waitUntil("Start node finished", timeout = 10.seconds) {
            workflowEngine.getWorkflowInstance(instance.id).getNode("start").status.finished
        }
        assertEquals(
            WorkflowInstanceNodeStatus.SUCCESS,
            workflowEngine.getWorkflowInstance(instance.id).getNode("start").status
        )
        assertEquals(1, mockWorkflowNodeExecutor.getTextsByInstanceId(instance.id).size)
    }

    @Test
    fun `A node taken over by another process is no longer changed by the process which lost it`() {
        val yaml = """
            name: Slow node
            nodes:
              - id: slow
                executorId: mock
                data:
                  text: Slow
                  waitMs: 3000
        """.trimIndent()
        val instanceId = workflowTestSupport.registerLaunchAndWaitForWorkflow(yaml, "TakenOver", wait = false)

        waitUntil("Slow node started", interval = 100.milliseconds, timeout = 10.seconds) {
            workflowEngine.getWorkflowInstance(instanceId).getNode("slow").status == WorkflowInstanceNodeStatus.STARTED
        }
        // Another process takes the node over
        committed {
            namedParameterJdbcTemplate.update(
                "UPDATE WKF_INSTANCE_NODES SET OWNER = :owner WHERE INSTANCE_ID = :id AND NODE_ID = 'slow'",
                mapOf("id" to instanceId, "owner" to uid("other-"))
            )
        }

        // The mock executor ran, but its result is not recorded
        waitUntil("Slow node executed", interval = 100.milliseconds, timeout = 10.seconds) {
            mockWorkflowNodeExecutor.getTextsByInstanceId(instanceId).isNotEmpty()
        }
        Thread.sleep(1_000)
        val node = workflowEngine.getWorkflowInstance(instanceId).getNode("slow")
        assertNotEquals(WorkflowInstanceNodeStatus.SUCCESS, node.status)
        assertNull(node.output)
    }
}
