package net.nemerosa.ontrack.job

import org.apache.commons.lang3.StringUtils
import java.time.Duration
import java.util.concurrent.TimeUnit

data class Schedule(
        val initialPeriod: Long = 0,
        val period: Long,
        val unit: TimeUnit,
        val cron: String? = null,
) {
    val periodText: String
        get() = when {
            cron != null && cron.isNotBlank() -> cron
            period <= 0 -> "Manually"
            period == 1L -> "Every " + StringUtils.substringBeforeLast(unit.name.lowercase(), "s")
            else -> "Every " + period + " " + unit.name.lowercase()
        }

    fun after(initial: Int): Schedule {
        check(cron == null || cron.isBlank()) { "Setting an initial delay is not supported for cron schedules." }
        return Schedule(
                initial.toLong(),
                period,
                unit
        )
    }

    /**
     * Initial delay, converted into the unit of the schedule
     */
    fun after(initial: Duration): Schedule {
        return after(unit.convert(initial).toInt())
    }

    companion object {

        fun cron(expression: String) = Schedule(
            initialPeriod = 0,
            period = 0,
            unit = TimeUnit.MILLISECONDS,
            cron = expression,
        )

        @JvmStatic
        fun everySeconds(seconds: Long): Schedule {
            return Schedule(0, seconds, TimeUnit.SECONDS)
        }

        @JvmStatic
        fun everyMinutes(minutes: Long): Schedule {
            return Schedule(0, minutes, TimeUnit.MINUTES)
        }

        @JvmField
        val NONE = everySeconds(0)
        @JvmField
        val EVERY_SECOND = everySeconds(1)
        @JvmField
        val EVERY_MINUTE = everyMinutes(1)
        @Suppress("unused")
        @JvmField
        val EVERY_HOUR = Schedule(0, 1, TimeUnit.HOURS)
        @JvmField
        val EVERY_DAY = Schedule(0, 1, TimeUnit.DAYS)
        @JvmField
        val EVERY_WEEK = Schedule(0, 7, TimeUnit.DAYS)
    }
}
