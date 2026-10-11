package net.nemerosa.ontrack.extension.workflows.engine

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.nemerosa.ontrack.extension.workflows.WorkflowConfigurationProperties
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorService
import net.nemerosa.ontrack.extension.workflows.repository.WorkflowInstanceRepository
import net.nemerosa.ontrack.it.MockSecurityService
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.model.tx.TransactionRetry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.sql.SQLTransientConnectionException
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A database failure while processing a node used to escape the node's coroutine entirely, leaving
 * the node in `WAITING` and the whole instance `RUNNING` forever. Whatever happens, a node must
 * always reach a terminal status.
 */
class WorkflowEngineNodeFailureTest {

    private lateinit var engine: WorkflowEngineImpl
    private lateinit var workflowInstanceRepository: WorkflowInstanceRepository

    private val instance = WorkflowInstanceFixtures.simpleLinear()

    @BeforeEach
    fun before() {
        workflowInstanceRepository = mockk(relaxed = true)
        every { workflowInstanceRepository.findWorkflowInstance(instance.id) } returns instance
        every { workflowInstanceRepository.claimNode(instance.id, any(), OWNER) } returns true
        every { workflowInstanceRepository.nodeStarted(instance.id, any(), OWNER) } returns true
        every { workflowInstanceRepository.nodeError(instance.id, any(), any(), any(), OWNER) } returns true
        // By default no retry: the failures used here are not transient, and the test must stay fast
        engine = engineWith(retries = 0)
    }

    private fun engineWith(retries: Int, parentWaitingMs: Long = 1_000): WorkflowEngineImpl {
        val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)
        every { transactionManager.getTransaction(any()) } returns SimpleTransactionStatus()

        val configProperties = OntrackConfigProperties().apply {
            tx.retries = retries
            tx.retryDelay = Duration.ZERO
            tx.retryMaxDelay = Duration.ZERO
        }

        return WorkflowEngineImpl(
            workflowInstanceRepository = workflowInstanceRepository,
            workflowNodeDispatcher = mockk(relaxed = true),
            workflowEngineProcess = mockk { every { ownerId() } returns OWNER },
            authenticationStorageService = mockk(relaxed = true),
            workflowNodeExecutorService = mockk<WorkflowNodeExecutorService>(relaxed = true),
            workflowConfigurationProperties = WorkflowConfigurationProperties().apply {
                parentWaitingInterval = Duration.ofMillis(parentWaitingMs)
            },
            securityService = MockSecurityService(),
            platformTransactionManager = transactionManager,
            transactionRetry = TransactionRetry(configProperties),
        )
    }

    /**
     * The node processing runs on its own coroutine: waiting for the error to be recorded.
     */
    private fun awaitNodeError(): String {
        val message = slot<String>()
        verify(timeout = 5_000) {
            workflowInstanceRepository.nodeError(instance.id, "start", capture(message), null, OWNER)
        }
        return message.captured
    }

    /**
     * The claim is made while the message is still in hand: a failure there is the queue's, and
     * leaves the node to be dispatched again by the recovery.
     */
    @Test
    fun `A database failure when claiming a node is left to the queue`() {
        every {
            workflowInstanceRepository.claimNode(instance.id, "start", OWNER)
        } throws RuntimeException("Database is down")

        assertFailsWith<RuntimeException> {
            engine.processNode(instance.id, "start")
        }

        verify(exactly = 0) { workflowInstanceRepository.nodeError(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `A node already claimed is not launched again`() {
        every { workflowInstanceRepository.claimNode(instance.id, "start", OWNER) } returns false

        engine.processNode(instance.id, "start")

        Thread.sleep(500)
        verify(exactly = 0) { workflowInstanceRepository.nodeStarted(any(), any(), any()) }
    }

    @Test
    fun `A database failure when starting a node marks the node in error and stops the instance`() {
        every {
            workflowInstanceRepository.nodeStarted(instance.id, "start", OWNER)
        } throws RuntimeException("Database is down")

        engine.processNode(instance.id, "start")

        assertEquals("Database is down", awaitNodeError())
        verify(timeout = 5_000) { workflowInstanceRepository.stopInstance(instance.id) }
    }

    @Test
    fun `A failure without a message is recorded with its class name`() {
        every {
            workflowInstanceRepository.nodeStarted(instance.id, "start", OWNER)
        } throws NullPointerException()

        engine.processNode(instance.id, "start")

        assertEquals(NullPointerException::class.java.name, awaitNodeError())
    }

    /**
     * The parent polling is the read which failed first in production, and it is not covered by the
     * transaction helper: it must carry its own retry.
     */
    @Test
    fun `A transient failure while polling the parents of a node is retried`() {
        val engine = engineWith(retries = 3)
        var attempts = 0
        every { workflowInstanceRepository.getNodeStatuses(instance.id, listOf("start")) } answers {
            attempts++
            if (attempts < 3) {
                throw CannotCreateTransactionException(
                    "Could not open JDBC Connection for transaction",
                    SQLTransientConnectionException("HikariPool-1 - Connection is not available"),
                )
            }
            mapOf("start" to WorkflowInstanceNodeStatus.SUCCESS)
        }

        engine.processNode(instance.id, "end")

        // The node went on to be started, so the parent status was eventually read
        verify(timeout = 5_000) { workflowInstanceRepository.nodeStarted(instance.id, "end", OWNER) }
        assertEquals(3, attempts)
    }

    @Test
    fun `A persistent failure while polling the parents of a node marks the node in error`() {
        val engine = engineWith(retries = 1)
        every {
            workflowInstanceRepository.getNodeStatuses(instance.id, listOf("start"))
        } throws CannotCreateTransactionException(
            "Could not open JDBC Connection for transaction",
            SQLTransientConnectionException("HikariPool-1 - Connection is not available"),
        )

        engine.processNode(instance.id, "end")

        verify(timeout = 5_000) {
            workflowInstanceRepository.nodeError(
                instance.id,
                "end",
                "Could not open JDBC Connection for transaction",
                null,
                OWNER,
            )
            workflowInstanceRepository.stopInstance(instance.id)
        }
    }

    /**
     * The security context lives in a thread local while the parent polling suspends on `delay`:
     * the node's coroutine can be resumed on any other thread of the IO dispatcher. Losing the
     * context there made the node execution fail its authentication check, which errored the node
     * and stopped the whole instance - at random.
     */
    @Test
    fun `The security context survives the wait for the parents of a node`() {
        val engine = engineWith(retries = 0, parentWaitingMs = 10)
        val authentication = TestingAuthenticationToken("test", "test")

        var calls = 0
        every { workflowInstanceRepository.getNodeStatuses(instance.id, listOf("start")) } answers {
            calls++
            if (calls == 1) {
                // Simulates the coroutine being resumed on a thread which knows nothing about it
                SecurityContextHolder.clearContext()
                mapOf("start" to WorkflowInstanceNodeStatus.STARTED)
            } else {
                mapOf("start" to WorkflowInstanceNodeStatus.SUCCESS)
            }
        }

        val authenticationAtStart = AtomicReference<Authentication?>()
        every { workflowInstanceRepository.nodeStarted(instance.id, "end", OWNER) } answers {
            authenticationAtStart.set(SecurityContextHolder.getContext().authentication)
            true
        }

        SecurityContextHolder.getContext().authentication = authentication
        try {
            engine.processNode(instance.id, "end")
        } finally {
            SecurityContextHolder.clearContext()
        }

        verify(timeout = 5_000) { workflowInstanceRepository.nodeStarted(instance.id, "end", OWNER) }
        assertEquals(authentication, authenticationAtStart.get())
    }

    @Test
    fun `A failure to record the node error does not escape the node coroutine`() {
        every {
            workflowInstanceRepository.nodeStarted(instance.id, "start", OWNER)
        } throws RuntimeException("Database is down")
        every {
            workflowInstanceRepository.nodeError(any(), any(), any(), any(), any())
        } throws RuntimeException("Database is still down")

        // Nothing thrown here, and nothing escaping to the coroutine handler either
        engine.processNode(instance.id, "start")

        verify(timeout = 5_000) {
            workflowInstanceRepository.nodeError(instance.id, "start", "Database is down", null, OWNER)
        }
    }

    companion object {
        private const val OWNER = "test-process"
    }
}
