package net.nemerosa.ontrack.extension.av.retry

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.common.TimeServer
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.audit.*
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.event.AutoVersioningEventService
import net.nemerosa.ontrack.extension.av.metrics.AutoVersioningMetricsService
import net.nemerosa.ontrack.extension.av.settings.AutoVersioningSettings
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.BranchFixtures
import net.nemerosa.ontrack.model.structure.Signature
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoVersioningRetryServiceImplTest {

    private val branch = BranchFixtures.testBranch()
    private val order = branch.createOrder(sourceProject = "source")

    private val now = LocalDateTime.of(2026, 9, 28, 12, 0)
    private val timeServer = object : TimeServer {
        override val now: LocalDateTime = this@AutoVersioningRetryServiceImplTest.now
    }

    private lateinit var auditQueryService: AutoVersioningAuditQueryService
    private lateinit var auditService: AutoVersioningAuditService
    private lateinit var auditStore: AutoVersioningAuditStore
    private lateinit var eventService: AutoVersioningEventService
    private lateinit var cachedSettingsService: CachedSettingsService
    private lateinit var metrics: AutoVersioningMetricsService
    private lateinit var service: AutoVersioningRetryServiceImpl

    private val transient = TransientException()

    @BeforeEach
    fun init() {
        auditQueryService = mockk()
        auditService = mockk(relaxed = true)
        auditStore = mockk()
        eventService = mockk(relaxed = true)
        cachedSettingsService = mockk()
        metrics = mockk(relaxed = true)
        service = AutoVersioningRetryServiceImpl(
            autoVersioningAuditQueryService = auditQueryService,
            autoVersioningAuditService = auditService,
            autoVersioningAuditStore = auditStore,
            autoVersioningEventService = eventService,
            cachedSettingsService = cachedSettingsService,
            metrics = metrics,
            timeServer = timeServer,
        )
        settings(retryMaxCount = 0)
        entry(order)
        noOtherRunningOrder()
    }

    @Test
    fun `No retry when retries are disabled`() {
        assertFalse(service.willRetry(order, transient))
    }

    @Test
    fun `Retry of a transient failure when retries are enabled`() {
        settings(retryMaxCount = 2)
        assertTrue(service.willRetry(order, transient))
    }

    @Test
    fun `Retry of a transient failure wrapped in another exception`() {
        settings(retryMaxCount = 2)
        assertTrue(service.willRetry(order, RuntimeException("Wrapper", IllegalStateException("Inner", transient))))
    }

    @Test
    fun `No retry of a non transient failure`() {
        settings(retryMaxCount = 2)
        assertFalse(service.willRetry(order, RuntimeException("Failed workflow")))
    }

    @Test
    fun `Retry while the attempt is below the maximum`() {
        settings(retryMaxCount = 2)
        entry(order, RETRY_ATTEMPT to "1")
        assertTrue(service.willRetry(order, transient))
    }

    @Test
    fun `No retry once the maximum is reached`() {
        settings(retryMaxCount = 2)
        entry(order, RETRY_ATTEMPT to "2")
        assertFalse(service.willRetry(order, transient))
    }

    @Test
    fun `No retry when a newer order for the same target is running`() {
        settings(retryMaxCount = 2)
        val newer = branch.createOrder(sourceProject = "source", targetVersion = "3.0.0")
        every { auditQueryService.findByFilter(any()) } returns listOf(
            auditEntry(order),
            auditEntry(newer),
        )
        assertFalse(service.willRetry(order, transient))
    }

    @Test
    fun `Retry attempt of an order which is not a retry`() {
        assertEquals(0, service.getRetryAttempt(order))
    }

    @Test
    fun `Retry attempt of an automatic retry`() {
        entry(order, RETRY_ATTEMPT to "2")
        assertEquals(2, service.getRetryAttempt(order))
    }

    @Test
    fun `A final failure is recorded as an error without any retry`() {
        service.onError(order, transient)

        verify(exactly = 1) { auditService.onError(order, transient) }
        verify(exactly = 0) { auditService.onCreated(any(), any()) }
        verify(exactly = 0) { metrics.onRetryScheduled(any()) }
        verify(exactly = 0) { metrics.onRetryExhausted(any()) }
    }

    @Test
    fun `A deferred transient failure which is not retried is notified`() {
        settings(retryMaxCount = 2)
        entry(order, RETRY_ATTEMPT to "2")

        service.onError(order, AutoVersioningDeferredErrorException("GitHub is down", transient))

        verify(exactly = 1) { auditService.onError(order, transient) }
        verify(exactly = 1) {
            eventService.sendError(order, "GitHub is down (failed after 2 automatic retries)", transient)
        }
    }

    @Test
    fun `A deferred transient failure which is retried is not notified`() {
        settings(retryMaxCount = 2)
        every { auditService.onCreated(any(), any()) } answers { auditEntry(firstArg()) }

        service.onError(order, AutoVersioningDeferredErrorException("GitHub is down", transient))

        verify(exactly = 1) { auditService.onCreated(any(), any()) }
        verify(exactly = 0) { eventService.sendError(any(), any(), any()) }
    }

    @Test
    fun `A non deferred failure is never notified by the retry service`() {
        service.onError(order, RuntimeException("Already notified"))

        verify(exactly = 0) { eventService.sendError(any(), any(), any()) }
    }

    @Test
    fun `The failure is recorded and notified even when the retry decision fails`() {
        settings(retryMaxCount = 2)
        every { auditQueryService.findByUUID(any(), any()) } throws IllegalStateException("DB is down")

        service.onError(order, AutoVersioningDeferredErrorException("GitHub is down", transient))

        verify(exactly = 1) { auditService.onError(order, transient) }
        verify(exactly = 1) { eventService.sendError(order, "GitHub is down", transient) }
    }

    @Test
    fun `The failure is recorded and notified when the retry cannot be created`() {
        settings(retryMaxCount = 2)
        every { auditService.onCreated(any(), any()) } throws IllegalStateException("DB is down")

        service.onError(order, AutoVersioningDeferredErrorException("GitHub is down", transient))

        verify(exactly = 1) { auditService.onError(order, transient) }
        verify(exactly = 1) { eventService.sendError(order, "GitHub is down", transient) }
    }

    @Test
    fun `A retryable failure schedules a new order`() {
        settings(retryMaxCount = 3, retryDelayMinutes = 10)
        val retryOrder = slot<AutoVersioningOrder>()
        val retryData = slot<Map<String, String>>()
        every { auditService.onCreated(capture(retryOrder), capture(retryData)) } answers {
            auditEntry(firstArg())
        }

        service.onError(order, transient)

        // New order
        assertTrue(retryOrder.isCaptured)
        val retry = retryOrder.captured
        assertTrue(retry.uuid != order.uuid, "New UUID")
        assertEquals(now.plusMinutes(10), retry.schedule, "Scheduled after the delay")
        assertEquals(order.copy(uuid = retry.uuid, schedule = retry.schedule), retry, "Same order otherwise")
        assertEquals(
            mapOf(
                RETRY_OF to order.uuid,
                RETRY_ORIGINAL to order.uuid,
                RETRY_ATTEMPT to "1",
                RETRY_MAX to "3",
            ),
            retryData.captured
        )
        verify(exactly = 1) { auditService.onPendingSchedule(retry) }
        // Failed order
        verify(exactly = 1) {
            auditService.onError(
                order,
                transient,
                RETRY_UUID to retry.uuid,
                RETRY_AT to Time.store(now.plusMinutes(10)),
            )
        }
        verify(exactly = 1) { metrics.onRetryScheduled(order) }
    }

    @Test
    fun `A retry of a retry keeps the original order`() {
        settings(retryMaxCount = 3)
        entry(order, RETRY_ATTEMPT to "1", RETRY_ORIGINAL to "original-uuid", RETRY_OF to "original-uuid")
        val retryData = slot<Map<String, String>>()
        every { auditService.onCreated(any(), capture(retryData)) } answers { auditEntry(firstArg()) }

        service.onError(order, transient)

        assertEquals("original-uuid", retryData.captured[RETRY_ORIGINAL])
        assertEquals(order.uuid, retryData.captured[RETRY_OF])
        assertEquals("2", retryData.captured[RETRY_ATTEMPT])
    }

    @Test
    fun `A retry failing for the last time counts as exhausted`() {
        settings(retryMaxCount = 2)
        entry(order, RETRY_ATTEMPT to "2")

        service.onError(order, transient)

        verify(exactly = 1) { auditService.onError(order, transient) }
        verify(exactly = 0) { auditService.onCreated(any(), any()) }
        verify(exactly = 1) { metrics.onRetryExhausted(order) }
    }

    @Test
    fun `Lineage of an order which is neither a retry nor rescheduled`() {
        settings(retryMaxCount = 2)
        every { auditStore.findUUIDsByCreationData(branch, RESCHEDULED_FROM, order.uuid) } returns emptyList()

        val lineage = service.getLineage(auditEntry(order))

        assertEquals(
            AutoVersioningAuditEntryLineage(
                retryAttempt = null,
                retryMax = null,
                retryOf = null,
                retryOriginal = null,
                retryUuid = null,
                retryAt = null,
                rescheduledFrom = null,
                rescheduledAs = emptyList(),
                configuredRetryMaxCount = 2,
            ),
            lineage
        )
    }

    @Test
    fun `Lineage of a failed automatic retry, itself retried and rescheduled`() {
        every { auditStore.findUUIDsByCreationData(branch, RESCHEDULED_FROM, order.uuid) } returns listOf("manual-uuid")
        val retryAt = now.plusMinutes(5)
        val entry = AutoVersioningAuditEntry(
            order = order,
            audit = listOf(
                state(
                    AutoVersioningAuditState.ERROR,
                    RETRY_UUID to "next-uuid",
                    RETRY_AT to Time.store(retryAt),
                ),
                state(
                    AutoVersioningAuditState.CREATED,
                    RETRY_OF to "previous-uuid",
                    RETRY_ORIGINAL to "original-uuid",
                    RETRY_ATTEMPT to "2",
                    RETRY_MAX to "3",
                ),
            ),
            routing = null,
            queue = null,
            upgradeBranch = null,
        )

        val lineage = service.getLineage(entry)

        assertEquals(2, lineage.retryAttempt)
        assertEquals(3, lineage.retryMax)
        assertEquals("previous-uuid", lineage.retryOf)
        assertEquals("original-uuid", lineage.retryOriginal)
        assertEquals("next-uuid", lineage.retryUuid)
        assertEquals(retryAt, lineage.retryAt)
        assertNull(lineage.rescheduledFrom)
        assertEquals(listOf("manual-uuid"), lineage.rescheduledAs)
        assertEquals(0, lineage.configuredRetryMaxCount)
    }

    private fun settings(retryMaxCount: Int, retryDelayMinutes: Int = 5) {
        every { cachedSettingsService.getCachedSettings(AutoVersioningSettings::class.java) } returns
                AutoVersioningSettings(
                    retryMaxCount = retryMaxCount,
                    retryDelayMinutes = retryDelayMinutes,
                )
    }

    private fun entry(order: AutoVersioningOrder, vararg creationData: Pair<String, String>) {
        every { auditQueryService.findByUUID(order.branch, order.uuid) } returns auditEntry(order, *creationData)
    }

    private fun noOtherRunningOrder() {
        every { auditQueryService.findByFilter(any()) } returns listOf(auditEntry(order))
    }

    private fun auditEntry(order: AutoVersioningOrder, vararg creationData: Pair<String, String>) =
        AutoVersioningAuditEntry(
            order = order,
            audit = listOf(
                state(AutoVersioningAuditState.POST_PROCESSING_START),
                state(AutoVersioningAuditState.CREATED, *creationData),
            ),
            routing = null,
            queue = null,
            upgradeBranch = null,
        )

    private fun state(state: AutoVersioningAuditState, vararg data: Pair<String, String>) =
        AutoVersioningAuditEntryState(
            signature = Signature.of("test"),
            state = state,
            data = data.toMap(),
        )

    private class TransientException : RuntimeException("GitHub is down"), AutoVersioningRetryableException

    companion object {
        private const val RETRY_OF = AutoVersioningAuditEntryStateDataKeys.RETRY_OF
        private const val RETRY_ORIGINAL = AutoVersioningAuditEntryStateDataKeys.RETRY_ORIGINAL
        private const val RETRY_ATTEMPT = AutoVersioningAuditEntryStateDataKeys.RETRY_ATTEMPT
        private const val RETRY_MAX = AutoVersioningAuditEntryStateDataKeys.RETRY_MAX
        private const val RETRY_UUID = AutoVersioningAuditEntryStateDataKeys.RETRY_UUID
        private const val RETRY_AT = AutoVersioningAuditEntryStateDataKeys.RETRY_AT
        private const val RESCHEDULED_FROM = AutoVersioningAuditEntryStateDataKeys.RESCHEDULED_FROM
    }
}
