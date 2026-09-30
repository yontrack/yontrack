package net.nemerosa.ontrack.extension.workflows.engine

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatcher
import net.nemerosa.ontrack.extension.workflows.WorkflowConfigurationProperties
import net.nemerosa.ontrack.extension.workflows.execution.WorkflowNodeExecutorService
import net.nemerosa.ontrack.extension.workflows.repository.WorkflowInstanceRepository
import net.nemerosa.ontrack.it.MockSecurityService
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.model.tx.TransactionRetry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.Duration

/**
 * A node whose parents are over is cancelled when it cannot start, for one of two reasons which
 * the user must be able to tell apart: a parent did not succeed, or the workflow was stopped
 * meanwhile - typically because a sibling node failed.
 */
class WorkflowEngineParentConditionsTest {

    private lateinit var engine: WorkflowEngineImpl
    private lateinit var workflowInstanceRepository: WorkflowInstanceRepository

    /**
     * `start` fans out to `fails` and `other`.
     */
    private val instance = WorkflowInstanceFixtures.fanOut()

    /**
     * The same instance once `fails` has failed and `nodeError` has stopped it: `other`, which was
     * still waiting for `start`, has been cancelled along with it.
     */
    private val stoppedInstance = instance.copy(
        nodesExecutions = listOf(
            instance.getNode("start").copy(status = WorkflowInstanceNodeStatus.SUCCESS),
            instance.getNode("fails").copy(status = WorkflowInstanceNodeStatus.ERROR),
            instance.getNode("other").copy(status = WorkflowInstanceNodeStatus.CANCELLED),
        )
    )

    @BeforeEach
    fun before() {
        workflowInstanceRepository = mockk(relaxed = true)
        every { workflowInstanceRepository.findWorkflowInstance(instance.id) } returns instance

        val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)
        every { transactionManager.getTransaction(any()) } returns SimpleTransactionStatus()

        engine = WorkflowEngineImpl(
            workflowInstanceRepository = workflowInstanceRepository,
            queueDispatcher = mockk<QueueDispatcher>(relaxed = true),
            workflowQueueProcessor = mockk(relaxed = true),
            workflowQueueSourceExtension = mockk(relaxed = true),
            workflowNodeExecutorService = mockk<WorkflowNodeExecutorService>(relaxed = true),
            workflowConfigurationProperties = WorkflowConfigurationProperties().apply {
                parentWaitingInterval = Duration.ofMillis(10)
            },
            securityService = MockSecurityService(),
            platformTransactionManager = transactionManager,
            transactionRetry = TransactionRetry(OntrackConfigProperties().apply { tx.retries = 0 }),
        )
    }

    @Test
    fun `A node whose parent did not succeed is cancelled because of its parents`() {
        every { workflowInstanceRepository.getNodeStatuses(instance.id, listOf("start")) } answers {
            // `start` failed, which also stopped the instance
            every { workflowInstanceRepository.findWorkflowInstance(instance.id) } returns stoppedInstance
            mapOf("start" to WorkflowInstanceNodeStatus.ERROR)
        }

        engine.processNode(instance.id, "other")

        verify(timeout = 5_000) {
            workflowInstanceRepository.nodeCancelled(instance.id, "other", "Parents conditions were not met.")
        }
        verify(exactly = 0) { workflowInstanceRepository.nodeStarted(any(), any()) }
    }

    @Test
    fun `A node whose parents succeeded is cancelled because the workflow was stopped when a sibling failed`() {
        var polls = 0
        every { workflowInstanceRepository.getNodeStatuses(instance.id, listOf("start")) } answers {
            polls++
            if (polls == 1) {
                // `other` is still polling its parent...
                mapOf("start" to WorkflowInstanceNodeStatus.STARTED)
            } else {
                // ... while `start` succeeded, `fails` started, failed at once and stopped the instance
                every { workflowInstanceRepository.findWorkflowInstance(instance.id) } returns stoppedInstance
                mapOf("start" to WorkflowInstanceNodeStatus.SUCCESS)
            }
        }

        engine.processNode(instance.id, "other")

        verify(timeout = 5_000) {
            workflowInstanceRepository.nodeCancelled(
                instance.id,
                "other",
                "The workflow was stopped before this node could start."
            )
        }
        verify(exactly = 0) {
            workflowInstanceRepository.nodeCancelled(instance.id, "other", "Parents conditions were not met.")
            workflowInstanceRepository.nodeStarted(any(), any())
        }
    }

    @Test
    fun `A node whose parents succeeded starts when the workflow is still running`() {
        every { workflowInstanceRepository.getNodeStatuses(instance.id, listOf("start")) } returns
                mapOf("start" to WorkflowInstanceNodeStatus.SUCCESS)

        engine.processNode(instance.id, "other")

        verify(timeout = 5_000) { workflowInstanceRepository.nodeStarted(instance.id, "other") }
        verify(exactly = 0) { workflowInstanceRepository.nodeCancelled(any(), any(), any()) }
    }
}
