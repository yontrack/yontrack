package net.nemerosa.ontrack.extension.av.retry

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditEntry
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditEntryStateDataKeys
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditQueryFilter
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditQueryService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditState
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningDispatcher
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.queue.AutoVersioningQueuePayload
import net.nemerosa.ontrack.extension.av.queue.AutoVersioningQueueProcessor
import net.nemerosa.ontrack.extension.av.settings.AutoVersioningSettings
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.extension.scm.mock.MockSCMTester
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Branch
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@QueueNoAsync
class AutoVersioningRetryIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var autoVersioningQueueProcessor: AutoVersioningQueueProcessor

    @Autowired
    private lateinit var autoVersioningAuditService: AutoVersioningAuditService

    @Autowired
    private lateinit var autoVersioningAuditQueryService: AutoVersioningAuditQueryService

    @Autowired
    private lateinit var autoVersioningDispatcher: AutoVersioningDispatcher

    @Test
    fun `No automatic retry when retries are disabled`() {
        withRetries(retryMaxCount = 0) {
            withTarget { target, sourceProject ->
                val order = target.failingOrder(sourceProject)

                process(order)

                val entry = target.entry(order)
                assertEquals(AutoVersioningAuditState.ERROR, entry.mostRecentState.state)
                assertNull(entry.mostRecentState.data[AutoVersioningAuditEntryStateDataKeys.RETRY_UUID])
                assertEquals(1, target.entries().size, "No retry has been created")
            }
        }
    }

    @Test
    fun `No automatic retry of a non transient failure`() {
        withRetries(retryMaxCount = 2) {
            withTarget { target, sourceProject ->
                val order = target.failingOrder(sourceProject, transient = false)

                process(order)

                val entry = target.entry(order)
                assertEquals(AutoVersioningAuditState.ERROR, entry.mostRecentState.state)
                assertEquals(1, target.entries().size, "No retry has been created")
            }
        }
    }

    @Test
    fun `Chain of automatic retries until the maximum is reached`() {
        withRetries(retryMaxCount = 2, retryDelayMinutes = 10) {
            withTarget { target, sourceProject ->
                val order = target.failingOrder(sourceProject)
                val start = Time.now

                // First failure
                process(order)
                val first = target.entry(order)
                assertEquals(AutoVersioningAuditState.ERROR, first.mostRecentState.state)
                val firstRetryUuid = assertNotNull(first.mostRecentState.data[AutoVersioningAuditEntryStateDataKeys.RETRY_UUID])

                // First retry, waiting for its schedule
                val firstRetry = target.entry(firstRetryUuid)
                assertEquals(AutoVersioningAuditState.PENDING_SCHEDULE, firstRetry.mostRecentState.state)
                val schedule = assertNotNull(firstRetry.order.schedule, "Retry is scheduled")
                assertTrue(schedule >= start.plusMinutes(10), "Retry is scheduled after the delay")
                assertEquals(
                    mapOf(
                        AutoVersioningAuditEntryStateDataKeys.RETRY_OF to order.uuid,
                        AutoVersioningAuditEntryStateDataKeys.RETRY_ORIGINAL to order.uuid,
                        AutoVersioningAuditEntryStateDataKeys.RETRY_ATTEMPT to "1",
                        AutoVersioningAuditEntryStateDataKeys.RETRY_MAX to "2",
                    ),
                    firstRetry.audit.last().data,
                )
                assertEquals(order.targetVersion, firstRetry.order.targetVersion)

                // Second failure
                process(firstRetry.order)
                val secondRetryUuid = assertNotNull(
                    target.entry(firstRetryUuid).mostRecentState.data[AutoVersioningAuditEntryStateDataKeys.RETRY_UUID]
                )
                val secondRetry = target.entry(secondRetryUuid)
                assertEquals(AutoVersioningAuditState.PENDING_SCHEDULE, secondRetry.mostRecentState.state)
                assertEquals(
                    mapOf(
                        AutoVersioningAuditEntryStateDataKeys.RETRY_OF to firstRetryUuid,
                        AutoVersioningAuditEntryStateDataKeys.RETRY_ORIGINAL to order.uuid,
                        AutoVersioningAuditEntryStateDataKeys.RETRY_ATTEMPT to "2",
                        AutoVersioningAuditEntryStateDataKeys.RETRY_MAX to "2",
                    ),
                    secondRetry.audit.last().data,
                )

                // Third & final failure
                process(secondRetry.order)
                val last = target.entry(secondRetryUuid)
                assertEquals(AutoVersioningAuditState.ERROR, last.mostRecentState.state)
                assertNull(last.mostRecentState.data[AutoVersioningAuditEntryStateDataKeys.RETRY_UUID])
                assertEquals(3, target.entries().size, "Original order and its two retries")

                // Only this final failure sends the error notification, which is checked by
                // AutoVersioningProcessingServiceImplPostProcessingErrorTest: the event is posted in its own
                // transaction, which does not see the branch created by the test transaction.
            }
        }
    }

    @Test
    fun `A newer order cancels a pending automatic retry`() {
        withRetries(retryMaxCount = 2) {
            withTarget { target, sourceProject ->
                val order = target.failingOrder(sourceProject)
                process(order)
                val retryUuid = assertNotNull(
                    target.entry(order).mostRecentState.data[AutoVersioningAuditEntryStateDataKeys.RETRY_UUID]
                )

                // A newer order comes in
                val newer = target.failingOrder(sourceProject, targetVersion = "3.0.0")
                autoVersioningAuditService.throttling(newer)
                autoVersioningAuditService.onCreated(newer)

                assertEquals(AutoVersioningAuditState.THROTTLED, target.entry(retryUuid).mostRecentState.state)
            }
        }
    }

    @Test
    fun `No automatic retry when a newer order for the same target is already running`() {
        withRetries(retryMaxCount = 2) {
            withTarget { target, sourceProject ->
                val order = target.failingOrder(sourceProject)
                autoVersioningAuditService.onCreated(order)
                // A newer order is waiting while the first one is processed
                val newer = target.failingOrder(sourceProject, targetVersion = "3.0.0")
                autoVersioningAuditService.onCreated(newer)

                process(order)

                val entry = target.entry(order)
                assertEquals(AutoVersioningAuditState.ERROR, entry.mostRecentState.state)
                assertNull(entry.mostRecentState.data[AutoVersioningAuditEntryStateDataKeys.RETRY_UUID])
                assertEquals(2, target.entries().size, "No retry has been created")
            }
        }
    }

    @Test
    fun `A manual reschedule of an exhausted order starts a fresh budget of retries`() {
        withRetries(retryMaxCount = 1) {
            withTarget { target, sourceProject ->
                val order = target.failingOrder(sourceProject)
                process(order)
                val retryUuid = assertNotNull(
                    target.entry(order).mostRecentState.data[AutoVersioningAuditEntryStateDataKeys.RETRY_UUID]
                )
                process(target.entry(retryUuid).order)
                assertEquals(AutoVersioningAuditState.ERROR, target.entry(retryUuid).mostRecentState.state)

                // Manual reschedule of the exhausted retry (processed right away, the queue being synchronous)
                val rescheduled = asAdmin { autoVersioningDispatcher.reschedule(target, retryUuid) }
                val rescheduledEntry = target.entry(rescheduled.uuid)
                assertEquals(
                    mapOf(AutoVersioningAuditEntryStateDataKeys.RESCHEDULED_FROM to retryUuid),
                    rescheduledEntry.audit.last().data,
                )

                // Fresh budget: the rescheduled order is retried again
                val nextRetryUuid = assertNotNull(
                    target.entry(rescheduled.uuid).mostRecentState.data[AutoVersioningAuditEntryStateDataKeys.RETRY_UUID],
                    "The rescheduled order is retried automatically"
                )
                assertEquals(
                    "1",
                    target.entry(nextRetryUuid).audit.last().data[AutoVersioningAuditEntryStateDataKeys.RETRY_ATTEMPT]
                )
            }
        }
    }

    @Test
    fun `Lineage of the audit entries in GraphQL`() {
        withRetries(retryMaxCount = 1) {
            withTarget { target, sourceProject ->
                val order = target.failingOrder(sourceProject)
                process(order)
                val retryUuid = assertNotNull(
                    target.entry(order).mostRecentState.data[AutoVersioningAuditEntryStateDataKeys.RETRY_UUID]
                )
                process(target.entry(retryUuid).order)
                val rescheduled = asAdmin { autoVersioningDispatcher.reschedule(target, retryUuid) }

                // Original order
                val original = lineage(order.uuid)
                assertEquals(retryUuid, original.path("retryUuid").asText())
                assertTrue(original.path("retryAt").isTextual, "Retry time is set")
                assertTrue(original.path("retryAttempt").isNull)
                assertEquals(1, original.path("configuredRetryMaxCount").asInt())

                // Its retry
                val retry = lineage(retryUuid)
                assertEquals(1, retry.path("retryAttempt").asInt())
                assertEquals(1, retry.path("retryMax").asInt())
                assertEquals(order.uuid, retry.path("retryOf").asText())
                assertEquals(order.uuid, retry.path("retryOriginal").asText())
                assertTrue(retry.path("retryUuid").isNull, "Exhausted retry is not retried")
                assertEquals(listOf(rescheduled.uuid), retry.path("rescheduledAs").values().map { it.asText() })

                // Its manual reschedule (processed right away, the queue being synchronous, and retried again)
                val manual = lineage(rescheduled.uuid)
                assertEquals(retryUuid, manual.path("rescheduledFrom").asText())
                assertTrue(manual.path("retryAttempt").isNull, "A manual reschedule is not an automatic retry")
                assertTrue(manual.path("retryUuid").isTextual, "A manual reschedule has a fresh budget of retries")
            }
        }
    }

    private fun lineage(uuid: String) = run(
        """
            {
                autoVersioningAuditEntries(filter: {uuid: "$uuid"}) {
                    pageItems {
                        lineage {
                            retryAttempt
                            retryMax
                            retryOf
                            retryOriginal
                            retryUuid
                            retryAt
                            rescheduledFrom
                            rescheduledAs
                            configuredRetryMaxCount
                        }
                    }
                }
            }
        """
    ).path("autoVersioningAuditEntries").path("pageItems").first().path("lineage")

    private fun process(order: AutoVersioningOrder) {
        if (autoVersioningAuditQueryService.findByUUID(order.branch, order.uuid) == null) {
            autoVersioningAuditService.onCreated(order)
        }
        autoVersioningQueueProcessor.process(AutoVersioningQueuePayload(order), null)
    }

    private fun Branch.failingOrder(
        sourceProject: String,
        transient: Boolean = true,
        targetVersion: String = "2.0.0",
    ) = createOrder(
        sourceProject = sourceProject,
        targetVersion = targetVersion,
    ).copy(
        postProcessing = FailingPostProcessing.ID,
        postProcessingConfig = FailingPostProcessingConfig(transient = transient).asJson(),
    )

    private fun Branch.entry(order: AutoVersioningOrder): AutoVersioningAuditEntry = entry(order.uuid)

    private fun Branch.entry(uuid: String): AutoVersioningAuditEntry = assertNotNull(
        autoVersioningAuditQueryService.findByUUID(this, uuid),
        "Audit entry $uuid found"
    )

    private fun Branch.entries() = autoVersioningAuditQueryService.findByFilter(
        AutoVersioningAuditQueryFilter(
            project = project.name,
            branch = name,
            count = 100,
        )
    )

    private fun withRetries(retryMaxCount: Int, retryDelayMinutes: Int = 5, code: () -> Unit) {
        withSettings(AutoVersioningSettings::class) {
            asAdmin {
                settingsManagerService.saveSettings(
                    AutoVersioningSettings(
                        retryMaxCount = retryMaxCount,
                        retryDelayMinutes = retryDelayMinutes,
                    )
                )
            }
            code()
        }
    }

    /**
     * Creates a source project and a target branch backed by a mock SCM repository.
     */
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
