package net.nemerosa.ontrack.service.job

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.scheduling.support.CronExpression
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The cron schedules of the jobs are written in the Spring syntax, and run as Quartz cron triggers.
 */
class QuartzCronTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "0 0 2 * * *",
            "0 */30 * * * *",
            "*/5 * * * * *",
            "0 0 9 * * MON-FRI",
            "0 0 9 * * 1-5",
            "0 0 9 * * 0",
            "0 0 9 * * 7",
            "0 0 9 * * 5-7",
            "0 0 9 * * SAT,SUN",
            "0 0 9 * * */2",
            "0 0 9 * * 1/2",
            "0 0 9 * * 5L",
            "0 0 9 * * MON#2",
            "0 0 9 * * 1#2",
            "0 0 9 1 * ?",
            "0 0 9 L * *",
            "0 0 9 15W * *",
            "0 15 10 ? * *",
            "0 0 12 1-7 JAN,JUL ?",
            "@daily",
            "@hourly",
            "@weekly",
            "@monthly",
            "@yearly",
        ]
    )
    fun `A Spring cron expression fires at the same times as its Quartz conversion`(spring: String) {
        val quartz = org.quartz.CronExpression(QuartzCron.of(spring)).apply {
            timeZone = TimeZone.getTimeZone(ZONE)
        }
        val expected = CronExpression.parse(spring)
        var springTime = START
        var quartzTime = Date.from(START.atZone(ZONE).toInstant())
        // Quartz computes the next times for the next hundred years only
        repeat(50) { index ->
            springTime = expected.next(springTime) ?: error("No next time for $spring")
            quartzTime = quartz.getNextValidTimeAfter(quartzTime) ?: error("No next time for ${QuartzCron.of(spring)}")
            assertEquals(
                springTime,
                LocalDateTime.ofInstant(quartzTime.toInstant(), ZONE),
                "Fire #$index of $spring, converted to ${QuartzCron.of(spring)}"
            )
        }
    }

    @Test
    fun `The day of the month and the day of the week cannot both be restricted`() {
        assertFailsWith<IllegalArgumentException> {
            QuartzCron.of("0 0 9 1 * MON")
        }
    }

    @Test
    fun `A cron expression has six fields`() {
        assertFailsWith<IllegalArgumentException> {
            QuartzCron.of("0 9 * * *")
        }
    }

    companion object {
        // No daylight saving time: the Spring expressions are evaluated on local times
        private val ZONE: ZoneId = ZoneId.of("UTC")
        private val START: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
    }
}
