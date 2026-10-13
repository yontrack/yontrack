package net.nemerosa.ontrack.service.job

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import net.nemerosa.ontrack.job.JobDecorator
import net.nemerosa.ontrack.job.JobKey
import net.nemerosa.ontrack.job.JobRegistration
import net.nemerosa.ontrack.job.JobRunListener
import net.nemerosa.ontrack.job.JobScope
import net.nemerosa.ontrack.job.Schedule
import net.nemerosa.ontrack.job.support.JobMetrics
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledFuture

/**
 * Runs the jobs of scope [JobScope.REPLICA] (#2078) on a local scheduler of this replica: each
 * replica runs them all, never in Quartz, with no coordination with the other replicas.
 *
 * They only refresh the memory of their pod: they are not on the jobs page, and have no history,
 * no pause, no "run now" and no timeout. Each run is timed by [JobMetrics.ontrack_job_duration],
 * like the runs of the cluster jobs, and each failure is logged and counted by
 * [JobMetrics.ontrack_job_replica_error].
 *
 * The jobs are given by the [JobRegistry] each time it is refreshed, and scheduled once this
 * scheduler is started: at a fixed rate, after their initial delay, or on their cron expression.
 * A job without any schedule never runs.
 */
@Component
class ReplicaJobScheduler(
    private val jobDecorator: JobDecorator,
    private val meterRegistry: MeterRegistry,
) : SmartLifecycle {

    private val logger: Logger = LoggerFactory.getLogger(ReplicaJobScheduler::class.java)

    /**
     * Registrations last given by the registry
     */
    @Volatile
    private var registrations: Map<JobKey, JobRegistration> = emptyMap()

    /**
     * Jobs scheduled on this replica
     */
    private val scheduled = mutableMapOf<JobKey, ScheduledJob>()

    private var taskScheduler: ThreadPoolTaskScheduler? = null

    /**
     * Keys of the jobs scheduled on this replica
     */
    val keys: Set<JobKey> @Synchronized get() = scheduled.keys.toSet()

    /**
     * After the [QuartzJobScheduler], which refreshes the registry when it starts
     */
    override fun getPhase(): Int = Int.MAX_VALUE - 900

    @Synchronized
    override fun isRunning(): Boolean = taskScheduler != null

    @Synchronized
    override fun start() {
        if (taskScheduler == null) {
            taskScheduler = ThreadPoolTaskScheduler().apply {
                poolSize = POOL_SIZE
                setThreadNamePrefix("replica-job-")
                isDaemon = true
                setWaitForTasksToCompleteOnShutdown(false)
                initialize()
            }
            logger.info("[job][replica] Starting {} job(s) running on every replica", registrations.size)
            apply()
        }
    }

    @Synchronized
    override fun stop() {
        scheduled.values.forEach { it.future?.cancel(false) }
        scheduled.clear()
        taskScheduler?.shutdown()
        taskScheduler = null
    }

    /**
     * Schedules the given jobs, and only them: the new ones are scheduled, the ones whose schedule
     * changed are rescheduled, the others are cancelled - letting a run in progress complete.
     *
     * @param registrations Jobs of scope [JobScope.REPLICA]
     * @param complete `false` when some jobs could not be collected: no job is then removed
     */
    @Synchronized
    fun reconcile(registrations: Collection<JobRegistration>, complete: Boolean = true) {
        val given = registrations
            .filter { it.scope == JobScope.REPLICA }
            .associateBy { it.job.key }
        this.registrations = if (complete) given else this.registrations + given
        if (taskScheduler != null) {
            apply()
        }
    }

    private fun apply() {
        val scheduler = taskScheduler ?: return
        // Removed or rescheduled
        scheduled.entries.removeIf { (key, job) ->
            val registration = registrations[key]
            if (registration == null || registration.schedule != job.schedule) {
                logger.debug("[job][replica]{} Unscheduled", key)
                job.future?.cancel(false)
                true
            } else {
                false
            }
        }
        // New or rescheduled
        registrations.values.forEach { registration ->
            val key = registration.job.key
            if (key !in scheduled) {
                scheduled[key] = schedule(scheduler, registration)
            }
        }
    }

    private fun schedule(scheduler: ThreadPoolTaskScheduler, registration: JobRegistration): ScheduledJob {
        val key = registration.job.key
        val schedule = registration.schedule
        val tags = listOf(
            Tag.of(JobMetrics.tag_job_category, key.type.category.key),
            Tag.of(JobMetrics.tag_job_type, key.type.key),
            Tag.of(JobMetrics.tag_job_key, key.id),
        )
        val run = Runnable {
            run(
                key = key,
                timer = meterRegistry.timer(JobMetrics.ontrack_job_duration, tags),
                errors = meterRegistry.counter(JobMetrics.ontrack_job_replica_error, tags),
            )
        }
        // Registered at once, so that they are there before any failure
        meterRegistry.timer(JobMetrics.ontrack_job_duration, tags)
        meterRegistry.counter(JobMetrics.ontrack_job_replica_error, tags)
        val future: ScheduledFuture<*>? = try {
            when {
                !schedule.cron.isNullOrBlank() -> scheduler.schedule(run, CronTrigger(schedule.cron!!))
                schedule.period > 0 -> scheduler.scheduleAtFixedRate(
                    run,
                    Instant.now().plus(duration(schedule.initialPeriod, schedule)),
                    duration(schedule.period, schedule),
                )

                else -> {
                    logger.warn("[job][replica]{} No schedule, the job never runs", key)
                    null
                }
            }
        } catch (any: IllegalArgumentException) {
            logger.error("[job][replica]$key The job cannot be scheduled with $schedule: ${any.message}")
            null
        }
        logger.debug("[job][replica]{} Scheduled with {}", key, schedule)
        return ScheduledJob(schedule, future)
    }

    private fun duration(amount: Long, schedule: Schedule): Duration =
        Duration.ofMillis(schedule.unit.toMillis(amount))

    /**
     * Runs the job of a key with its last registration
     */
    private fun run(key: JobKey, timer: Timer, errors: Counter) {
        val job = registrations[key]?.job ?: return
        try {
            if (!job.isValid) {
                logger.debug("[job][replica]{} Not valid, not run", key)
                return
            }
            if (job.isDisabled) {
                logger.debug("[job][replica]{} Disabled, not run", key)
                return
            }
        } catch (any: Exception) {
            errors.increment()
            logger.error("[job][replica]$key Cannot check the state of the job", any)
            return
        }
        val listener = JobRunListener { progress ->
            logger.debug("[job][replica][progress]{} {}", key, progress.text)
        }
        val sample = Timer.start(meterRegistry)
        try {
            jobDecorator.decorate(job) { job.task.run(listener) }()
        } catch (any: Exception) {
            errors.increment()
            logger.error("[job][replica]$key Failure", any)
        } finally {
            sample.stop(timer)
        }
    }

    private class ScheduledJob(
        val schedule: Schedule,
        val future: ScheduledFuture<*>?,
    )

    companion object {
        /**
         * Threads of the local scheduler: the jobs of a replica are short and few
         */
        private const val POOL_SIZE = 2
    }
}
