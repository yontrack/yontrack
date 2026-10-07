package net.nemerosa.ontrack.service.events

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.job.JobCategory
import net.nemerosa.ontrack.job.JobScheduler
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventFilter
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.events.EventsCleanupService
import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.settings.EventsSettings
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Retention of the events, through the [EventsSettings] and [EventsCleanupService].
 *
 * The `EVENTS` table is shared by all the tests, which post their events at the current time or
 * around 2020. The events of these tests are posted in 1985, and the cleanup counts the retention
 * back from a time in 1985, so that it never deletes the events of other tests.
 */
@AsAdminTest
class EventsCleanupIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var eventsCleanupService: EventsCleanupService

    @Autowired
    private lateinit var eventPostService: EventPostService

    @Autowired
    private lateinit var eventQueryService: EventQueryService

    @Autowired
    private lateinit var jobScheduler: JobScheduler

    /**
     * Time the retention is counted back from.
     */
    private val now: LocalDateTime = LocalDateTime.of(1985, 6, 1, 12, 0, 0)

    private fun postEvent(user: String, time: LocalDateTime) {
        val project = project()
        asAdmin {
            eventPostService.post(
                Event.of(EventFactory.UPDATE_PROJECT).with(Signature.of(time, user)).withProject(project).build()
            )
        }
    }

    /**
     * Times of the events posted by the [user], newest first.
     */
    private fun eventTimes(user: String): List<LocalDateTime?> =
        eventQueryService.findEvents(EventFilter(user = user), 0, 100).pageItems.map { it.signature?.time }

    private fun withRetention(retentionDays: Int, code: () -> Unit) {
        withSettings<EventsSettings> {
            settingsManagerService.saveSettings(EventsSettings(retentionDays = retentionDays))
            code()
        }
    }

    private fun withBatchSize(batchSize: Int, code: () -> Unit) {
        val old = ontrackConfigProperties.events.cleanup.batchSize
        ontrackConfigProperties.events.cleanup.batchSize = batchSize
        try {
            code()
        } finally {
            ontrackConfigProperties.events.cleanup.batchSize = old
        }
    }

    @Test
    fun `The retention is disabled by default`() {
        withCleanSettings<EventsSettings> {
            assertEquals(0, settingsService.getCachedSettings(EventsSettings::class.java).retentionDays)
        }
    }

    @Test
    fun `Saving and reading the retention`() {
        withRetention(30) {
            assertEquals(30, settingsService.getCachedSettings(EventsSettings::class.java).retentionDays)
        }
    }

    @Test
    fun `A negative retention is rejected`() {
        withCleanSettings<EventsSettings> {
            assertFailsWith<InputException> {
                settingsManagerService.saveSettings(EventsSettings(retentionDays = -1))
            }
            assertEquals(0, settingsService.getCachedSettings(EventsSettings::class.java).retentionDays)
        }
    }

    @Test
    fun `No event is deleted when the retention is disabled`() {
        val user = uid("ev")
        postEvent(user, now.minusDays(400))
        postEvent(user, now.minusDays(10))
        withRetention(0) {
            assertEquals(0, eventsCleanupService.cleanup(now))
        }
        assertEquals(
            listOf(now.minusDays(10), now.minusDays(400)),
            eventTimes(user),
        )
    }

    @Test
    fun `The events older than the retention are deleted, across several batches`() {
        val user = uid("ev")
        // Five events older than 30 days, two newer
        listOf(31L, 40L, 100L, 200L, 365L).forEach { days ->
            postEvent(user, now.minusDays(days))
        }
        postEvent(user, now.minusDays(29))
        postEvent(user, now.minusDays(1))
        withBatchSize(2) {
            withRetention(30) {
                assertEquals(5, eventsCleanupService.cleanup(now))
            }
        }
        assertEquals(
            listOf(now.minusDays(1), now.minusDays(29)),
            eventTimes(user),
        )
    }

    @Test
    fun `The cleanup job is registered as a core job`() {
        val key = jobScheduler.getAllJobKeys().find {
            it.type.category.key == JobCategory.CORE.key && it.type.key == "events-cleanup"
        }
        assertNotNull(key, "The events cleanup job is registered") {
            assertEquals("Events cleanup", it.type.name)
        }
    }

}
