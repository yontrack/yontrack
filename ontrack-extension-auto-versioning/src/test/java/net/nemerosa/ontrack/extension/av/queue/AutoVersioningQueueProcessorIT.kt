package net.nemerosa.ontrack.extension.av.queue

import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditEntry
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditQueryService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditState
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Branch
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The processing of the auto-versioning orders received from the queue, when two consumers share
 * a queue during a rolling upgrade.
 *
 * Not transactional: the orders are processed on their own threads, like on their own instances.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AutoVersioningQueueProcessorIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var autoVersioningQueueProcessor: AutoVersioningQueueProcessor

    @Autowired
    private lateinit var autoVersioningAuditService: AutoVersioningAuditService

    @Autowired
    private lateinit var autoVersioningAuditQueryService: AutoVersioningAuditQueryService

    @Autowired
    private lateinit var trackingPostProcessing: TrackingPostProcessing

    @Test
    fun `Two orders for the same target branch processed by two consumers run one after the other`() {
        withTarget { target, sourceProject ->
            val first = target.trackedOrder(sourceProject, targetVersion = "2.0.0")
            val second = target.trackedOrder(sourceProject, targetVersion = "2.1.0")
            assertEquals(
                AutoVersioningQueuePayload(first).routingIdentifier(),
                AutoVersioningQueuePayload(second).routingIdentifier(),
                "Same routing identifier"
            )
            autoVersioningAuditService.onCreated(first)
            autoVersioningAuditService.onCreated(second)

            // Two consumers, one per instance, each with one of the orders
            val consumers = Executors.newFixedThreadPool(2)
            try {
                val processings = listOf(first, second).map { order ->
                    consumers.submit {
                        autoVersioningQueueProcessor.process(AutoVersioningQueuePayload(order), null)
                    }
                }
                processings.forEach { it.get(120, TimeUnit.SECONDS) }
            } finally {
                consumers.shutdownNow()
            }

            val firstInterval = assertNotNull(trackingPostProcessing.interval(first), "First order post-processed")
            val secondInterval = assertNotNull(trackingPostProcessing.interval(second), "Second order post-processed")
            assertFalse(
                firstInterval.overlaps(secondInterval),
                "Orders for the same target branch are processed one after the other"
            )
            listOf(first, second).forEach { order ->
                val state = target.entry(order).mostRecentState.state
                assertFalse(state.isRunning, "Order ${order.targetVersion} processed to its end")
                assertTrue(state != AutoVersioningAuditState.ERROR, "Order ${order.targetVersion} processed without error")
            }
        }
    }

    @Test
    fun `An order which is no longer running when received is not processed`() {
        withTarget { target, sourceProject ->
            val order = target.trackedOrder(sourceProject, targetVersion = "2.0.0")
            autoVersioningAuditService.onCreated(order)
            // A newer order for the same target throttles this one before it is received
            autoVersioningAuditService.throttling(target.trackedOrder(sourceProject, targetVersion = "2.1.0"))
            assertEquals(AutoVersioningAuditState.THROTTLED, target.entry(order).mostRecentState.state)

            autoVersioningQueueProcessor.process(AutoVersioningQueuePayload(order), null)

            val entry = target.entry(order)
            assertEquals(AutoVersioningAuditState.THROTTLED, entry.mostRecentState.state, "Order left throttled")
            assertTrue(
                entry.audit.none { it.state == AutoVersioningAuditState.RECEIVED || it.state == AutoVersioningAuditState.PROCESSING_START },
                "Order neither received nor processed"
            )
            assertEquals(null, trackingPostProcessing.interval(order), "Order not post-processed")
        }
    }

    private fun Branch.trackedOrder(
        sourceProject: String,
        targetVersion: String,
    ): AutoVersioningOrder = createOrder(
        sourceProject = sourceProject,
        targetVersion = targetVersion,
    ).copy(
        postProcessing = TrackingPostProcessing.ID,
        postProcessingConfig = TrackingPostProcessingConfig(durationMs = 1_000).asJson(),
    )

    private fun Branch.entry(order: AutoVersioningOrder): AutoVersioningAuditEntry = assertNotNull(
        autoVersioningAuditQueryService.findByUUID(this, order.uuid),
        "Audit entry ${order.uuid} found"
    )

    private fun withTarget(
        code: MockSCMTester.MockSCMRepositoryContext.(target: Branch, sourceProject: String) -> Unit,
    ) {
        asAdmin {
            val source = doCreateProject()
            mockSCMTester.withMockSCMRepository {
                project {
                    branch {
                        configureMockSCMBranch()
                        repositoryFile(
                            path = "gradle.properties",
                            content = "version = 1.0.0",
                        )
                        code(this, source.name)
                    }
                }
            }
        }
    }

}
