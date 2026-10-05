package net.nemerosa.ontrack.job.support

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.job.*
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * @property meterRegistry If set, the scheduler will register job metrics
 */
class DefaultJobScheduler
@JvmOverloads
constructor(
    private val jobDecorator: JobDecorator,
    private val scheduler: TaskExecutor,
    private val jobListener: JobListener,
    initiallyPaused: Boolean,
    private val jobExecutorService: Executor,
    private val scattering: Boolean,
    private val scatteringRatio: Double,
    private val meterRegistry: MeterRegistry? = null,
    private val timeout: Duration? = null,
    private val jobHistoryRecorder: JobHistoryRecorder? = null,
    timeoutControllerInterval: Duration? = null,
) : JobScheduler {

    private val logger = LoggerFactory.getLogger(JobScheduler::class.java)

    private val services = ConcurrentHashMap(TreeMap<JobKey, JobScheduledService>())
    private val schedulerPaused: AtomicBoolean

    private val idGenerator = AtomicLong()

    private fun MeterRegistry.statusGauge(
        metricName: String,
        statusFilterFn: (JobStatus) -> Boolean,
    ) {
        gauge(
            metricName,
            services
        ) {
            it.filter { (_, service) -> statusFilterFn(service.jobStatus) }
                .size.toDouble()
        }
    }

    init {
        Validate.inclusiveBetween(0.0, 1.0, scatteringRatio)
        this.schedulerPaused = AtomicBoolean(initiallyPaused)
        // Metrics
        if (meterRegistry != null) {
            // count
            meterRegistry.gaugeMapSize(
                JobMetrics.ontrack_job_count_total,
                emptyList(),
                services
            )
            meterRegistry.statusGauge(JobMetrics.ontrack_job_running_total) { it.isRunning }
            meterRegistry.statusGauge(JobMetrics.ontrack_job_disabled_total) { it.isDisabled }
            meterRegistry.statusGauge(JobMetrics.ontrack_job_paused_total) { it.isPaused }
            meterRegistry.statusGauge(JobMetrics.ontrack_job_error_total) { it.isError }
            meterRegistry.statusGauge(JobMetrics.ontrack_job_timeout_total) { it.isTimeout }
            meterRegistry.statusGauge(JobMetrics.ontrack_job_invalid_total) { !it.isValid }
        }
        // Scheduling the timeout controller job
        if (timeoutControllerInterval != null) {
            scheduler.scheduleAtFixedDelay(
                createTimeoutControllerJob(),
                timeoutControllerInterval,
                timeoutControllerInterval
            )
        }
    }

    private fun createTimeoutControllerJob() = Runnable {
        val stopped = checkForTimeouts()
        logger.debug("[scheduler] $stopped job(s) have been stopped because of timeout")
    }

    override fun checkForTimeouts(): Int = services.values.count { jobScheduledService ->
        // And checks them for timeout
        jobScheduledService.checkForTimeout()
    }

    override fun schedule(job: Job, schedule: Schedule) {
        logger.debug("[scheduler][job]{} Scheduling with {}", job.key, schedule)
        // Manages existing schedule
        val existingService = services[job.key]
        if (existingService != null) {
            logger.debug("[scheduler][job]{} Modifying existing schedule", job.key)
            existingService.update(
                job,
                schedule
            )
        }
        // Creates and starts the scheduled service
        else {
            logger.debug("[scheduler][job]{} Starting scheduled service", job.key)
            // Copy stats from old schedule
            val jobScheduledService = JobScheduledService(
                initialJob = job,
                initialSchedule = schedule,
                pausedAtStartup = jobListener.isPausedAtStartup(job.key)
            )
            // Registration
            services[job.key] = jobScheduledService
        }
    }

    override fun unschedule(key: JobKey): Boolean {
        return unschedule(key, true)
    }

    private fun unschedule(key: JobKey, forceStop: Boolean): Boolean {
        logger.debug("[scheduler][job]{} Unscheduling job", key)
        val existingService = services.remove(key)
        return if (existingService != null) {
            logger.debug("[scheduler][job]{} Stopping running job", key)
            existingService.cancel(forceStop)
            true
        } else {
            false
        }
    }

    override fun pause() {
        schedulerPaused.set(true)
    }

    override fun resume() {
        schedulerPaused.set(false)
    }

    override fun isPaused(): Boolean {
        return schedulerPaused.get()
    }

    override fun pause(key: JobKey): Boolean {
        val existingService = services[key]
        if (existingService != null) {
            existingService.pause()
            return true
        } else {
            throw JobNotScheduledException(key)
        }
    }

    override fun resume(key: JobKey): Boolean {
        val existingService = services[key]
        if (existingService != null) {
            existingService.resume()
            return true
        } else {
            throw JobNotScheduledException(key)
        }
    }

    override fun getJobStatus(key: JobKey): Optional<JobStatus> {
        val existingService = services[key]
        return if (existingService != null) {
            Optional.of(existingService.jobStatus)
        } else {
            Optional.empty()
        }
    }

    override fun getJobKey(id: Long): Optional<JobKey> {
        return services.values.stream()
            .filter { service -> service.id == id }
            .map { it.jobKey }
            .findFirst()
    }

    override fun stop(key: JobKey): Boolean {
        val existingService = services[key]
        return existingService?.stop() ?: throw JobNotScheduledException(key)
    }

    override fun getAllJobKeys(): Collection<JobKey> {
        return services.keys
    }

    override fun getJobKeysOfType(type: JobType): Collection<JobKey> {
        return allJobKeys
            .filter { key -> key.sameType(type) }
            .toSet()
    }

    override fun getJobKeysOfCategory(category: JobCategory): Collection<JobKey> {
        return allJobKeys
            .filter { key -> key.sameCategory(category) }
            .toSet()
    }

    override fun getJobStatuses(): Collection<JobStatus> {
        return services.values
            .map { it.jobStatus }
            .sortedBy { it.id }
            .toList()
    }

    override fun fireImmediately(jobKey: JobKey): Optional<CompletableFuture<*>> {
        // Gets the existing scheduled service
        val jobScheduledService = services[jobKey] ?: throw JobNotScheduledException(jobKey)
        // Fires the job immediately
        return jobScheduledService.doRun(true)
    }

    private inner class JobScheduledService(
        initialJob: Job,
        initialSchedule: Schedule,
        pausedAtStartup: Boolean,
    ) : Runnable {

        private var job = initialJob
        private var schedule = initialSchedule

        val id: Long = idGenerator.incrementAndGet()
        private var actualSchedule: Schedule = Schedule.NONE
        private var scheduledFuture: ScheduledFuture<*>? = null

        private val paused: AtomicBoolean = AtomicBoolean(pausedAtStartup)

        private val currentExecution = AtomicReference<Future<*>>()
        private val runProgress = AtomicReference<JobRunProgress>()
        private val runCount = AtomicLong()
        private val startTime = AtomicLong()
        private val lastRunDate = AtomicReference<LocalDateTime>()
        private val lastRunDurationMs = AtomicLong()
        private val lastErrorCount = AtomicLong()
        private val lastTimeoutCount = AtomicLong()
        private val lastError = AtomicReference<String>(null)

        init {
            // Paused at startup
            if (pausedAtStartup) {
                logger.debug("[job]{} Job paused at startup", job.key)
            }
            // Initial schedule
            createSchedule()
        }

        private fun createSchedule() {
            val cron = schedule.cron
            if (cron.isNullOrBlank()) {
                // Converting all units to milliseconds
                var initialPeriod = TimeUnit.MILLISECONDS.convert(schedule.initialPeriod, schedule.unit)
                val period = TimeUnit.MILLISECONDS.convert(schedule.period, schedule.unit)
                // Scattering
                if (scattering) {
                    // Computes the hash for the job key
                    val hash = abs(job.key.toString().hashCode()) % 10000
                    // Period to consider
                    val scatteringMax = (period * scatteringRatio).toLong()
                    if (scatteringMax > 0) {
                        // Modulo on the period
                        val delay = hash * scatteringMax / 10000
                        logger.debug("[job]{} Scattering enabled - additional delay: {} ms", job.key, delay)
                        // Adding to the initial delay
                        initialPeriod += delay
                    }
                }
                // Actual schedule
                actualSchedule = Schedule(
                    initialPeriod,
                    period,
                    TimeUnit.MILLISECONDS
                )
                // Scheduling now
                scheduledFuture = if (schedule.period > 0) {
                    scheduler.scheduleAtFixedDelay(
                        this,
                        Duration.ofMillis(initialPeriod),
                        Duration.ofMillis(period)
                    )
                } else {
                    logger.debug("[job]{} Job not scheduled since period = 0", job.key)
                    null
                }
            } else {
                scheduledFuture = scheduler.scheduleCron(this, cron)
            }
        }

        /**
         * Updates (if needed) the service to use the new job.
         */
        fun update(
            newJob: Job,
            newSchedule: Schedule,
        ) {
            // Checks the key of the job
            if (job.key != newJob.key) {
                throw IllegalStateException(
                    "The job assigned to a job service " +
                            "cannot have a different key. " +
                            "Expected=${job.key}, Actual=${newJob.key}"
                )
            }
            // Adapting the schedule if needed
            if (newSchedule != schedule) {
                // Cancels current execution service (NOT any currently running job!)
                cancel(false)
                // Changes the schedule
                schedule = newSchedule
                // Reschedules
                createSchedule()
            }
            // Replacing the job itself
            job = newJob
        }

        val jobKey: JobKey = job.key

        private val run: Runnable
            get() {
                val jobRunListener = DefaultJobRunListener()
                val rootTask = { job.task.run(jobRunListener) }
                val decoratedTask = jobDecorator.decorate(job, rootTask)
                val runnable = MonitoredRun(decoratedTask, object : MonitoredRunListenerAdapter() {

                    var start: LocalDateTime? = null
                    var error: String? = null

                    override fun onStart() {
                        start = Time.now
                        error = null
                    }

                    override fun onFailure(ex: Exception) {
                        error = ex.message ?: ex::class.java.name
                    }

                    override fun onCompletion() {
                        logger.debug("[job][task]{} Removed job execution", job.key)
                        currentExecution.set(null)
                        // Recording the metrics
                        val end = Time.now
                        start?.let {
                            val duration = Duration.between(it, end)
                            meterRegistry?.timer(
                                JobMetrics.ontrack_job_duration,
                                listOf(
                                    Tag.of(JobMetrics.tag_job_category, jobKey.type.category.key),
                                    Tag.of(JobMetrics.tag_job_type, jobKey.type.key),
                                    Tag.of(JobMetrics.tag_job_key, jobKey.id),
                                )
                            )?.record(duration.toMillis(), TimeUnit.MILLISECONDS)
                            // Recording the history
                            jobHistoryRecorder?.record(
                                jobKey = jobKey,
                                startedAt = it,
                                endedAt = end,
                                error = error,
                            )
                        }
                    }
                })
                val monitoredRunListener = object : MonitoredRunListener {
                    override fun onStart() {
                        logger.debug("[job][task]{} On start", job.key)
                        lastRunDate.set(Time.now())
                        startTime.set(System.currentTimeMillis())
                        runCount.incrementAndGet()
                        jobListener.onJobStart(job.key)
                    }

                    override fun onSuccess(duration: Long) {
                        lastRunDurationMs.set(duration)
                        logger.debug("[job][task]{} Success in {} ms", job.key, duration)
                        jobListener.onJobEnd(job.key, duration)
                        lastErrorCount.set(0)
                        lastError.set(null)
                        lastTimeoutCount.set(0)
                    }

                    override fun onFailure(ex: Exception) {
                        lastErrorCount.incrementAndGet()
                        lastError.set(ex.message)
                        lastTimeoutCount.set(0)
                        logger.debug("[job][task]{} Failure: {}", job.key, ex.message)
                        try {
                            jobListener.onJobError(jobStatus, ex)
                        } catch (uncaught: Exception) {
                            logger.error("[job][task]${job.key} Could not process error for job because of:", uncaught)
                            logger.error("[job][task]${job.key} Initial error for job:", ex)
                        }
                    }

                    override fun onCompletion() {
                        runProgress.set(null)
                        startTime.set(0)
                        logger.debug("[job][task]{} Job completed.", job.key)
                        jobListener.onJobComplete(job.key)
                    }
                }
                return MonitoredRun(runnable, monitoredRunListener)
            }

        val jobStatus: JobStatus
            get() {
                val valid = job.isValid
                return JobStatus(
                    id = id,
                    key = job.key,
                    schedule = schedule,
                    actualSchedule = actualSchedule,
                    description = job.description,
                    isRunning = currentExecution.get() != null,
                    isValid = valid,
                    isPaused = paused.get(),
                    isDisabled = job.isDisabled,
                    progress = runProgress.get(),
                    runCount = runCount.get(),
                    lastRunDate = lastRunDate.get(),
                    lastRunDurationMs = lastRunDurationMs.get(),
                    nextRunDate = getNextRunDate(valid),
                    lastErrorCount = lastErrorCount.get(),
                    lastTimeoutCount = lastTimeoutCount.get(),
                    lastError = lastError.get()
                )
            }

        override fun run() {
            if (!schedulerPaused.get()) {
                doRun(false)
            }
        }

        fun doRun(force: Boolean): Optional<CompletableFuture<*>> {
            logger.debug("[job][run]{} Trying to run now - forced = {}", job.key, force)
            if (job.isValid) {
                if (job.isDisabled) {
                    logger.debug("[job][run]{} Not allowed to run now because disabled", job.key)
                    return Optional.empty()
                } else if (paused.get() && !force) {
                    logger.debug("[job][run]{} Not allowed to run now because paused", job.key)
                    return Optional.empty()
                } else {
                    // Registers the execution before submitting the task, since the task
                    // clears it on completion, possibly before the submission returns
                    val execution = CompletableFuture<Void?>()
                    if (!currentExecution.compareAndSet(null, execution)) {
                        logger.debug("[job][run]{} Not allowed to run now because already running", job.key)
                        return Optional.empty()
                    }
                    // Task to run
                    val taskRun = run
                    // Scheduling
                    logger.debug("[job][run]{} Job task submitted asynchronously", job.key)
                    try {
                        jobExecutorService.execute {
                            // Stopped before having started
                            if (execution.isDone) return@execute
                            try {
                                taskRun.run()
                                execution.complete(null)
                            } catch (any: Throwable) {
                                execution.completeExceptionally(any)
                            }
                        }
                    } catch (any: RejectedExecutionException) {
                        currentExecution.compareAndSet(execution, null)
                        throw any
                    }
                    return Optional.of(execution)
                }
            } else {
                logger.debug("[job][run]{} Not valid - removing from schedule", job.key)
                unschedule(job.key, false)
                return Optional.empty()
            }
        }

        /**
         * Checks if the job is in timeout and if yes, [stops][stop] it.
         *
         * @return True if the job _was_ in timeout and had to be stopped.
         */
        fun checkForTimeout(): Boolean {
            val timeout = job.timeout ?: this@DefaultJobScheduler.timeout
            return if (timeout != null) {
                // Is this job running?
                if (currentExecution.get() != null) {
                    // We take the actual start date of the run
                    val start = startTime.get()
                    if (start != 0L) {
                        // Current execution time of this job
                        val now = System.currentTimeMillis()
                        val elapsed = now - start
                        // logger.debug("[job][timeout]{} Timeout - start:   {}", job.key, start)
                        // logger.debug("[job][timeout]{} Timeout - now:     {}", job.key, now)
                        // logger.debug("[job][timeout]{} Timeout - elasped: {}", job.key, elapsed)
                        // logger.debug("[job][timeout]{} Timeout - timeout: {}", job.key, timeout.toMillis())
                        // If this time exceeds the timeout
                        if (elapsed >= timeout.toMillis()) {
                            // Logging
                            logger.info("[job][timeout]{} Timeout - stopping the job", job.key)
                            // We stop the job
                            stop()
                            // Metrics for this job
                            lastTimeoutCount.incrementAndGet()
                            // We assume it's been stopped
                            true
                        }
                        // Still under the timeout, we keep running
                        else {
                            logger.debug("[job][timeout]{} Timeout - still OK", job.key)
                            false
                        }
                    } else {
                        // Not started yet, won't be stopped
                        logger.debug("[job][timeout]{} Timeout - not started", job.key)
                        false
                    }
                }
                // Job is not running, won't be stopped
                else {
                    logger.debug("[job][timeout]{} Timeout - not running", job.key)
                    false
                }
            }
            // No timeout, so won't be stopped
            else {
                logger.debug("[job][timeout]{} Timeout - not configured for timeout", job.key)
                false
            }
        }

        fun stop(): Boolean {
            logger.debug("[job]{} Stopping job", job.key)
            return currentExecution.updateAndGet { current ->
                current?.cancel(true)
                null
            } == null
        }

        fun cancel(forceStop: Boolean): Boolean {
            logger.debug("[job]{} Cancelling job (forcing = {})", job.key, forceStop)
            if (forceStop) {
                stop()
            }
            return scheduledFuture?.cancel(forceStop) ?: false
        }

        private fun getNextRunDate(valid: Boolean): LocalDateTime? {
            return if (valid) {
                scheduledFuture
                    ?.getDelay(TimeUnit.SECONDS)
                    ?.let { Time.now().plus(it, ChronoUnit.SECONDS) }
            } else {
                null
            }
        }

        fun pause() {
            if (scheduledFuture != null) {
                paused.set(true)
                jobListener.onJobPaused(job.key)
            }
        }

        fun resume() {
            if (scheduledFuture != null) {
                paused.set(false)
                jobListener.onJobResumed(job.key)
            }
        }

        private inner class DefaultJobRunListener : JobRunListener {

            override fun progress(progress: JobRunProgress) {
                jobListener.onJobProgress(job.key, progress)
                logger.debug(
                    "[job][progress]{} {}",
                    job.key,
                    progress.text
                )
                runProgress.set(progress)
            }

        }

    }
}
