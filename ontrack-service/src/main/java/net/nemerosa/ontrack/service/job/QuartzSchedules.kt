package net.nemerosa.ontrack.service.job

import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.Schedule
import org.quartz.CronScheduleBuilder
import org.quartz.SimpleScheduleBuilder
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.quartz.TriggerKey
import java.time.Instant
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.quartz.JobKey as QuartzJobKey

/**
 * How the jobs of Yontrack are identified and scheduled in Quartz.
 *
 * - a job is a Quartz job whose group is made of the keys of its category and type, and whose name
 *   is its ID;
 * - its trigger, if any, has the same name and group;
 * - a periodic schedule is a simple trigger at a fixed rate, starting after its initial delay plus
 *   its scattering;
 * - a cron schedule is a cron trigger;
 * - a job with no schedule has no trigger;
 * - a trigger which misfires fires once at once, then continues.
 *
 * The `JOBS` repository joins the triggers on the same naming.
 */
object QuartzSchedules {

    /**
     * Maximum length of the names and groups of the jobs and triggers in the tables of Quartz
     */
    const val MAX_NAME_LENGTH = 200

    fun group(key: JobKey): String = "${key.type.category.key}/${key.type.key}"

    fun jobKey(key: JobKey): QuartzJobKey = QuartzJobKey(key.id, group(key))

    fun triggerKey(key: JobKey): TriggerKey = TriggerKey(key.id, group(key))

    /**
     * Trigger of a job, for its schedule.
     *
     * @param scattering Ratio of the period over which the start of a periodic job is scattered,
     * 0 for no scattering
     * @param now Current time
     * @return The trigger, or `null` for a job with no schedule
     */
    fun trigger(key: JobKey, schedule: Schedule, scattering: Double, now: Instant): Trigger? {
        val cron = schedule.cron
        return if (!cron.isNullOrBlank()) {
            TriggerBuilder.newTrigger()
                .withIdentity(triggerKey(key))
                .forJob(jobKey(key))
                .withSchedule(
                    CronScheduleBuilder.cronSchedule(QuartzCron.of(cron))
                        .withMisfireHandlingInstructionFireAndProceed()
                )
                .build()
        } else if (schedule.period > 0) {
            val actual = actualSchedule(key, schedule, scattering)
            TriggerBuilder.newTrigger()
                .withIdentity(triggerKey(key))
                .forJob(jobKey(key))
                .startAt(Date.from(now.plusMillis(actual.initialPeriod)))
                .withSchedule(
                    SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMilliseconds(actual.period)
                        .repeatForever()
                        .withMisfireHandlingInstructionNowWithRemainingCount()
                )
                .build()
        } else {
            null
        }
    }

    /**
     * Schedule of a job, in milliseconds, with its scattering added to its initial delay.
     *
     * The scattering is a fraction of the period, given by the hash of the key of the job: jobs of
     * the same period do not all start at the same time.
     */
    fun actualSchedule(key: JobKey, schedule: Schedule, scattering: Double): Schedule {
        if (!schedule.cron.isNullOrBlank() || schedule.period <= 0) {
            return schedule
        }
        val period = TimeUnit.MILLISECONDS.convert(schedule.period, schedule.unit)
        var initial = TimeUnit.MILLISECONDS.convert(schedule.initialPeriod, schedule.unit)
        val scatteringMax = (period * scattering).toLong()
        if (scatteringMax > 0) {
            val hash = abs(key.toString().hashCode() % HASH_RANGE)
            initial += hash * scatteringMax / HASH_RANGE
        }
        return Schedule(initial, period, TimeUnit.MILLISECONDS)
    }

    private const val HASH_RANGE = 10_000L
}
