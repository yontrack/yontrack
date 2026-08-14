package net.nemerosa.ontrack.extension.notifications.processing

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.nemerosa.ontrack.extension.notifications.channels.NotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NotificationChannelRegistry
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.extension.notifications.channels.ValidatedNotificationChannelConfig
import net.nemerosa.ontrack.extension.notifications.model.Notification
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecord
import net.nemerosa.ontrack.extension.notifications.recording.NotificationRecordingService
import net.nemerosa.ontrack.it.MockSecurityService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.emptyEventContext
import net.nemerosa.ontrack.model.events.SimpleEventType
import net.nemerosa.ontrack.model.tx.TransactionHelper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.CannotCreateTransactionException
import java.sql.SQLTransientConnectionException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Checks that a failure to *record* a notification never changes, nor masks, the outcome of the
 * notification itself. See the connection pool exhaustion incident: recording failures were turning
 * successfully sent notifications into errors, and were hiding the real cause of genuine failures.
 */
class DefaultNotificationProcessingServiceTest {

    private lateinit var service: DefaultNotificationProcessingService
    private lateinit var notificationRecordingService: NotificationRecordingService
    private lateinit var transactionHelper: TransactionHelper
    private lateinit var channelRegistry: NotificationChannelRegistry
    private lateinit var channel: NotificationChannel<String, String>

    private val event = Event(
        eventType = SimpleEventType(
            id = "test_event",
            template = "Test event",
            description = "Test event",
            context = emptyEventContext(),
        ),
        signature = null,
        entities = emptyMap(),
        extraEntities = emptyMap(),
        ref = null,
        values = emptyMap(),
    )

    private val notification = Notification(
        source = null,
        channel = "test",
        channelConfig = "config".asJson(),
        event = event,
        template = null,
    )

    @BeforeEach
    fun before() {
        notificationRecordingService = mockk(relaxed = true)

        transactionHelper = mockk()
        runsTransactions()

        channel = mockk()
        every { channel.validate(any()) } returns ValidatedNotificationChannelConfig.config("config")

        channelRegistry = mockk()
        every { channelRegistry.findChannel("test") } returns channel

        service = DefaultNotificationProcessingService(
            notificationChannelRegistry = channelRegistry,
            notificationRecordingService = notificationRecordingService,
            meterRegistry = SimpleMeterRegistry(),
            transactionHelper = transactionHelper,
            securityService = MockSecurityService(),
        )
    }

    /**
     * By default, the transaction helper just runs the code.
     */
    private fun runsTransactions() {
        every { transactionHelper.inNewTransaction(any<() -> Any>()) } answers {
            @Suppress("UNCHECKED_CAST")
            (it.invocation.args[0] as () -> Any).invoke()
        }
    }

    /**
     * The database is unreachable: any recording attempt fails the way it did in production.
     */
    private fun databaseIsDown() {
        every { transactionHelper.inNewTransaction(any<() -> Any>()) } throws CannotCreateTransactionException(
            "Could not open JDBC Connection for transaction",
            SQLTransientConnectionException("HikariPool-1 - Connection is not available"),
        )
    }

    private fun publishes(result: NotificationResult<String>) {
        every { channel.publish(any(), any(), any(), any(), any(), any()) } returns result
    }

    private fun publishFails(error: Throwable) {
        every { channel.publish(any(), any(), any(), any(), any(), any()) } throws error
    }

    private fun process(): NotificationResult<*>? =
        service.process(notification, emptyMap()) { _, _ -> }?.result

    @Test
    fun `A notification which was sent is not reported as an error when its recording fails`() {
        publishes(NotificationResult.ok("sent"))
        databaseIsDown()

        val result = process()

        assertNotNull(result) {
            assertEquals(NotificationResultType.OK, it.type, "The notification was actually sent")
            assertEquals("sent", it.output)
        }
    }

    @Test
    fun `A failing recording does not mask the actual failure of a notification`() {
        publishFails(RuntimeException("Channel is unreachable"))
        databaseIsDown()

        val result = process()

        assertNotNull(result) {
            assertEquals(NotificationResultType.ERROR, it.type)
            assertEquals("Channel is unreachable", it.message, "The original failure is preserved")
        }
    }

    @Test
    fun `A failure without a message is reported with its class name`() {
        publishFails(NullPointerException())

        val result = process()

        assertNotNull(result) {
            assertEquals(NotificationResultType.ERROR, it.type)
            assertEquals(NullPointerException::class.java.name, it.message)
        }
    }

    @Test
    fun `The actual result of the channel is recorded`() {
        publishes(NotificationResult.ok("sent"))
        val record = slot<NotificationRecord>()
        every { notificationRecordingService.record(capture(record)) } returns "id"

        process()

        assertEquals(NotificationResultType.OK, record.captured.result.type)
    }

    @Test
    fun `An unknown channel is not processed`() {
        every { channelRegistry.findChannel("test") } returns null
        assertNull(service.process(notification, emptyMap()) { _, _ -> })
    }

    @Test
    fun `An invalid configuration is recorded and does not throw when the database is down`() {
        every { channel.validate(any()) } returns ValidatedNotificationChannelConfig.error<String>("Invalid")
        databaseIsDown()

        val result = process()

        assertNull(result, "No channel result for an invalid configuration")
    }
}
