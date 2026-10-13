package net.nemerosa.ontrack.service.job

import net.nemerosa.ontrack.job.JobCategory
import net.nemerosa.ontrack.job.Schedule
import org.quartz.CronTrigger
import org.quartz.SimpleTrigger
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuartzSchedulesTest {

    private val key = JobCategory.of("test").getType("schedules").getKey("one")
    private val now = Instant.parse("2026-10-13T10:00:00Z")

    @Test
    fun `A job is identified in Quartz by its category and type, and its ID`() {
        val quartzKey = QuartzSchedules.jobKey(key)
        assertEquals("test/schedules", quartzKey.group)
        assertEquals("one", quartzKey.name)
    }

    @Test
    fun `A job with no schedule has no trigger`() {
        assertNull(QuartzSchedules.trigger(key, Schedule.NONE, scattering = 0.0, now = now))
    }

    @Test
    fun `A periodic job runs at a fixed rate, from its initial delay`() {
        val trigger = QuartzSchedules.trigger(key, Schedule.everyMinutes(10).after(Duration.ofMinutes(1)), scattering = 0.0, now = now)
        assertIs<SimpleTrigger>(trigger)
        assertEquals(TimeUnit.MINUTES.toMillis(10), trigger.repeatInterval)
        assertEquals(SimpleTrigger.REPEAT_INDEFINITELY, trigger.repeatCount)
        assertEquals(now.plus(Duration.ofMinutes(1)), trigger.startTime.toInstant())
        assertEquals(QuartzSchedules.triggerKey(key), trigger.key)
        assertEquals(QuartzSchedules.jobKey(key), trigger.jobKey)
    }

    @Test
    fun `A periodic job which misfires fires once now, then continues`() {
        val trigger = QuartzSchedules.trigger(key, Schedule.everyMinutes(10), scattering = 0.0, now = now)
        assertIs<SimpleTrigger>(trigger)
        assertEquals(SimpleTrigger.MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_REMAINING_REPEAT_COUNT, trigger.misfireInstruction)
    }

    @Test
    fun `The start of a periodic job is scattered within a fraction of its period, by the hash of its key`() {
        val period = Duration.ofMinutes(10)
        val keys = (1..50).map { JobCategory.of("test").getType("schedules").getKey("job-$it") }
        val starts = keys.map { key ->
            val trigger = QuartzSchedules.trigger(key, Schedule.everyMinutes(10), scattering = 0.5, now = now)
            assertIs<SimpleTrigger>(trigger)
            // Same key, same start
            assertEquals(
                trigger.startTime,
                QuartzSchedules.trigger(key, Schedule.everyMinutes(10), scattering = 0.5, now = now)?.startTime
            )
            Duration.between(now, trigger.startTime.toInstant())
        }
        assertTrue(starts.all { it >= Duration.ZERO && it < period.dividedBy(2) }, "Within half the period: $starts")
        assertTrue(starts.distinct().size > 40, "Scattered: $starts")
    }

    @Test
    fun `A cron job runs as a Quartz cron trigger, and fires once now on a misfire`() {
        val trigger = QuartzSchedules.trigger(key, Schedule.cron("0 0 2 * * *"), scattering = 1.0, now = now)
        assertIs<CronTrigger>(trigger)
        assertEquals("0 0 2 * * ?", trigger.cronExpression)
        assertEquals(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW, trigger.misfireInstruction)
    }

    @Test
    fun `The actual schedule of a periodic job includes its scattering`() {
        val actual = QuartzSchedules.actualSchedule(key, Schedule.everyMinutes(10), scattering = 0.0)
        assertEquals(Schedule(0, TimeUnit.MINUTES.toMillis(10), TimeUnit.MILLISECONDS), actual)
        val scattered = QuartzSchedules.actualSchedule(key, Schedule.everyMinutes(10), scattering = 1.0)
        assertTrue(scattered.initialPeriod in 0 until TimeUnit.MINUTES.toMillis(10))
    }
}
