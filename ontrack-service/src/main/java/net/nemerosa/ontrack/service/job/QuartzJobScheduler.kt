package net.nemerosa.ontrack.service.job

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.support.JobHistoryRecorder
import net.nemerosa.ontrack.job.support.JobMetrics
import net.nemerosa.ontrack.job.support.JobNotScheduledException
import net.nemerosa.ontrack.job.support.MonitoredRun
import net.nemerosa.ontrack.job.support.MonitoredRunListener
import net.nemerosa.ontrack.job.support.MonitoredRunListenerAdapter
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.repository.JobRecord
import net.nemerosa.ontrack.repository.JobRecordRepository
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.quartz.JobBuilder
import org.quartz.Scheduler
import org.quartz.SchedulerException
import org.quartz.SimpleScheduleBuilder
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.quartz.TriggerKey
import org.quartz.impl.jdbcjobstore.Constants
import org.quartz.impl.matchers.GroupMatcher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.quartz.JobKey as QuartzJobKey

/**
 * Job scheduler on Quartz, clustered through the database (#2077).
 *
 * - The jobs known by this replica are in the [JobRegistry], refreshed from the providers and the
 *   suppliers at startup, then every `ontrack.config.jobs.orchestration` minutes.
 * - The jobs of the cluster are the rows of `JOBS` - giving them their ID - and the Quartz jobs and
 *   triggers. They are [reconciled][reconcile] with the registry at startup, by the orchestration
 *   job, once per period in the cluster, and on [refresh].
 * - A fire runs the job of its key in the registry. A key unknown here refreshes the registry once;
 *   still unknown, the fire is skipped.
 * - The pause of a job is stored in `JOBS` and in Quartz, the pause of all the jobs is the one of
 *   Quartz: both apply to every replica and survive restarts.
 * - The running state, progress and counters of the runs are those of this replica only (#2079
 *   shares them), as are the stop of a run and the timeouts.
 *
 * Every write to Quartz and to `JOBS` runs in a transaction of its own, committed on return
 * whatever the transaction of the caller, so that no trigger waits for the caller's commit, and
 * that no lock of Quartz is held for longer than the write.
 *
 * Quartz is started once the jobs of this replica are reconciled: this scheduler starts in the
 * phase before the one of Quartz.
 */
@Component
class QuartzJobScheduler(
    private val jobRegistry: JobRegistry,
    private val jobRecordRepository: JobRecordRepository,
    private val schedulerProvider: ObjectProvider<Scheduler>,
    private val jobSchedulerName: JobSchedulerName,
    private val jobDecorator: JobDecorator,
    private val jobListener: JobListener,
    private val jobHistoryRecorder: JobHistoryRecorder,
    private val meterRegistry: MeterRegistry,
    private val ontrackConfigProperties: OntrackConfigProperties,
    transactionManager: PlatformTransactionManager,
) : JobScheduler, SmartLifecycle {

    private val logger: Logger = LoggerFactory.getLogger(JobScheduler::class.java)

    /**
     * Writes to Quartz and to `JOBS`, in their own transaction
     */
    private val writeTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    @Volatile
    private var started = false

    private var timer: ScheduledExecutorService? = null

    /**
     * Runs and their counters, on this replica
     */
    private val runtimes = ConcurrentHashMap<JobKey, JobRuntime>()

    /**
     * Fires requested by this replica, by trigger, completed when their run is over
     */
    private val pendingFires = ConcurrentHashMap<TriggerKey, PendingFire>()

    private val scheduler: Scheduler get() = schedulerProvider.getObject()

    private val name: String get() = jobSchedulerName.value

    private val scattering: Double
        get() = if (ontrackConfigProperties.jobs.scattering) ontrackConfigProperties.jobs.scatteringRatio else 0.0

    init {
        meterRegistry.gauge(JobMetrics.ontrack_job_count_total, this) { it.countStatuses { true } }
        meterRegistry.gauge(JobMetrics.ontrack_job_running_total, this) { it.countStatuses { status -> status.isRunning } }
        meterRegistry.gauge(JobMetrics.ontrack_job_disabled_total, this) { it.countStatuses { status -> status.isDisabled } }
        meterRegistry.gauge(JobMetrics.ontrack_job_paused_total, this) { it.countStatuses { status -> status.isPaused } }
        meterRegistry.gauge(JobMetrics.ontrack_job_error_total, this) { it.countStatuses { status -> status.isError } }
        meterRegistry.gauge(JobMetrics.ontrack_job_timeout_total, this) { it.countStatuses { status -> status.isTimeout } }
        meterRegistry.gauge(JobMetrics.ontrack_job_invalid_total, this) { it.countStatuses { status -> !status.isValid } }
    }

    private fun countStatuses(filter: (JobStatus) -> Boolean): Double =
        if (started) {
            try {
                jobStatuses.count(filter).toDouble()
            } catch (any: Exception) {
                logger.debug("[job] Cannot count the jobs", any)
                0.0
            }
        } else {
            0.0
        }

    // ============================================================================================
    // Lifecycle
    // ============================================================================================

    /**
     * Just before Quartz, which starts in the last phase
     */
    override fun getPhase(): Int = Int.MAX_VALUE - 1000

    override fun isRunning(): Boolean = started

    override fun start() {
        logger.info("[job] Reconciling the jobs of this replica with the cluster ($name)")
        jobRegistry.refresh()
        reconcile()
        @Suppress("DEPRECATION")
        if (ontrackConfigProperties.jobs.pausedAtStartup) {
            logger.warn("[job] ontrack.config.jobs.paused-at-startup is set: pausing all the jobs, on every replica")
            pause()
        }
        started = true
        timer = Executors.newSingleThreadScheduledExecutor(
            BasicThreadFactory.builder().daemon(true).namingPattern("job-registry-%d").build()
        ).apply {
            val orchestration = Duration.ofMinutes(ontrackConfigProperties.jobs.orchestration.toLong())
            scheduleWithFixedDelay(::refreshRegistry, orchestration.toMillis(), orchestration.toMillis(), TimeUnit.MILLISECONDS)
            val timeoutInterval = ontrackConfigProperties.jobs.timeoutControllerInterval
            scheduleWithFixedDelay(::controlTimeouts, timeoutInterval.toMillis(), timeoutInterval.toMillis(), TimeUnit.MILLISECONDS)
            scheduleWithFixedDelay(::sweepPendingFires, SWEEP_INTERVAL_MS, SWEEP_INTERVAL_MS, TimeUnit.MILLISECONDS)
        }
    }

    override fun stop() {
        started = false
        timer?.shutdownNow()
        timer = null
    }

    private fun checkStarted() {
        check(started) { "The job scheduler is not started yet" }
    }

    /**
     * Refreshes the registry of this replica, unless it was refreshed in the last half period, by
     * the orchestration job for example.
     */
    private fun refreshRegistry() {
        val period = TimeUnit.MINUTES.toMillis(ontrackConfigProperties.jobs.orchestration.toLong())
        if (System.currentTimeMillis() - jobRegistry.lastRefresh >= period / 2) {
            try {
                jobRegistry.refresh()
            } catch (any: Exception) {
                logger.error("[job] Cannot refresh the jobs of this replica", any)
            }
        }
    }

    private fun controlTimeouts() {
        try {
            val stopped = checkForTimeouts()
            logger.debug("[job] {} job(s) have been stopped because of timeout", stopped)
        } catch (any: Exception) {
            logger.error("[job] Cannot check the timeouts of the jobs", any)
        }
    }

    /**
     * Completes the fires requested here whose trigger is gone: run, by another replica.
     */
    private fun sweepPendingFires() {
        if (pendingFires.isEmpty()) return
        try {
            pendingFires.forEach { (triggerKey, fire) ->
                if (fire.created && !scheduler.checkExists(triggerKey)) {
                    pendingFires.remove(triggerKey, fire)
                    fire.future.complete(null)
                }
            }
        } catch (any: Exception) {
            logger.debug("[job] Cannot check the pending fires", any)
        }
    }

    // ============================================================================================
    // Reconciliation
    // ============================================================================================

    override fun refresh() {
        jobRegistry.refresh()
        if (started) {
            reconcile()
        }
    }

    /**
     * Reconciles the jobs of the cluster with the registry of this replica: rows of `JOBS`, Quartz
     * jobs and triggers are created for the new jobs, updated for the changed ones, and removed for
     * the ones which no longer exist - unless the registry could not collect all the jobs.
     */
    private fun reconcile() {
        writeTransaction.executeWithoutResult {
            jobRecordRepository.lockForReconciliation(name)
            val records = jobRecordRepository.findAll(name).associateBy { it.key }
            val quartzJobs = scheduler.getJobKeys(GroupMatcher.anyJobGroup())
            val quartzTriggers = scheduler.getTriggerKeys(GroupMatcher.anyTriggerGroup())
            val desired = jobRegistry.all.filter { registration -> fitsQuartz(registration.job.key) }
            desired.forEach { registration ->
                try {
                    reconcile(registration, records[registration.job.key], quartzJobs, quartzTriggers)
                } catch (any: Exception) {
                    logger.error("[job] Cannot reconcile the job ${registration.job.key}", any)
                }
            }
            if (jobRegistry.complete) {
                val keys = desired.map { it.job.key }.toSet()
                records.values.filter { it.key !in keys }.forEach { record ->
                    logger.info("[job] Removing the job ${record.key}, which no longer exists")
                    deleteQuartzJob(QuartzSchedules.jobKey(record.key))
                    jobRecordRepository.delete(name, record.id)
                }
                val quartzKeys = keys.map { QuartzSchedules.jobKey(it) }.toSet()
                (quartzJobs - quartzKeys).forEach { orphan ->
                    deleteQuartzJob(orphan)
                }
            }
        }
    }

    private fun reconcile(
        registration: JobRegistration,
        record: JobRecord?,
        quartzJobs: Set<QuartzJobKey>,
        quartzTriggers: Set<TriggerKey>,
    ) {
        val job = registration.job
        val key = job.key
        val schedule = registration.schedule
        val jobKey = QuartzSchedules.jobKey(key)
        val triggerKey = QuartzSchedules.triggerKey(key)
        if (record == null) {
            logger.debug("[job] Creating the job {}", key)
            val created = jobRecordRepository.create(name, key, job.description, schedule)
            createQuartzJob(key, schedule, created.paused)
        } else {
            if (record.schedule != schedule || record.description != job.description ||
                record.key.type.name != key.type.name || record.key.type.category.name != key.type.category.name
            ) {
                jobRecordRepository.update(name, record.id, key, job.description, schedule)
            }
            if (jobKey !in quartzJobs) {
                createQuartzJob(key, schedule, record.paused)
            } else if (record.schedule != schedule || (triggerKey !in quartzTriggers && hasTrigger(schedule))) {
                logger.debug("[job] Rescheduling the job {} with {}", key, schedule)
                scheduler.unscheduleJob(triggerKey)
                scheduleTrigger(key, schedule, record.paused)
            }
        }
    }

    /**
     * Deletes a Quartz job and its triggers. A one-shot trigger of the job may be removed by Quartz
     * meanwhile, once its fire is over, which fails the deletion: it is then tried again.
     */
    private fun deleteQuartzJob(jobKey: QuartzJobKey) {
        try {
            scheduler.deleteJob(jobKey)
        } catch (_: SchedulerException) {
            try {
                scheduler.deleteJob(jobKey)
            } catch (any: SchedulerException) {
                logger.error("[job] Cannot delete the Quartz job $jobKey", any)
            }
        }
    }

    private fun hasTrigger(schedule: Schedule) = !schedule.cron.isNullOrBlank() || schedule.period > 0

    private fun createQuartzJob(key: JobKey, schedule: Schedule, paused: Boolean) {
        scheduler.addJob(
            JobBuilder.newJob(QuartzJobRunner::class.java)
                .withIdentity(QuartzSchedules.jobKey(key))
                .storeDurably(true)
                .requestRecovery(false)
                .build(),
            true,
        )
        scheduleTrigger(key, schedule, paused)
    }

    private fun scheduleTrigger(key: JobKey, schedule: Schedule, paused: Boolean) {
        val trigger = try {
            QuartzSchedules.trigger(key, schedule, scattering, Instant.now())
        } catch (any: IllegalArgumentException) {
            logger.error("[job] The job $key cannot be scheduled with $schedule: ${any.message}")
            null
        }
        if (trigger != null) {
            scheduler.scheduleJob(trigger)
        }
        if (paused) {
            scheduler.pauseJob(QuartzSchedules.jobKey(key))
        }
    }

    /**
     * Makes sure a job of this replica exists in the cluster.
     */
    private fun ensure(registration: JobRegistration): JobRecord {
        val key = registration.job.key
        val existing = jobRecordRepository.findByKey(name, key)
        if (existing != null && scheduler.checkExists(QuartzSchedules.jobKey(key))) {
            return existing
        }
        jobRecordRepository.lockForReconciliation(name)
        val record = jobRecordRepository.findByKey(name, key)
            ?: jobRecordRepository.create(name, key, registration.job.description, registration.schedule)
        if (!scheduler.checkExists(QuartzSchedules.jobKey(key))) {
            createQuartzJob(key, registration.schedule, record.paused)
        }
        return record
    }

    private fun fitsQuartz(key: JobKey): Boolean {
        val fits = key.id.length <= QuartzSchedules.MAX_NAME_LENGTH - FIRE_PREFIX_LENGTH &&
                QuartzSchedules.group(key).length <= QuartzSchedules.MAX_NAME_LENGTH
        if (!fits) {
            logger.error("[job] The key of the job $key is too long to be scheduled")
        }
        return fits
    }

    // ============================================================================================
    // Runs
    // ============================================================================================

    /**
     * Runs the job of a Quartz job, on a thread of Quartz.
     */
    fun execute(quartzJobKey: QuartzJobKey, triggerKey: TriggerKey) {
        var failure: Throwable? = null
        try {
            val registration = jobRegistry.find(quartzJobKey) ?: run {
                logger.debug("[job] Job {} unknown on this replica, refreshing the jobs", quartzJobKey)
                jobRegistry.refresh()
                jobRegistry.find(quartzJobKey)
            }
            if (registration == null) {
                logger.debug("[job] Job {} unknown on this replica, skipping its run", quartzJobKey)
            } else {
                run(registration.job)
            }
        } catch (any: Throwable) {
            failure = any
        } finally {
            pendingFires.remove(triggerKey)?.let { fire ->
                if (failure != null) fire.future.completeExceptionally(failure) else fire.future.complete(null)
            }
        }
    }

    private fun run(job: Job) {
        val key = job.key
        if (!job.isValid) {
            logger.debug("[job][run]{} Not valid, not run", key)
            return
        }
        if (job.isDisabled) {
            logger.debug("[job][run]{} Disabled, not run", key)
            return
        }
        val runtime = runtimes.computeIfAbsent(key) { JobRuntime() }
        runtime.running.set(RunningJob(Thread.currentThread(), System.currentTimeMillis()))
        try {
            monitoredRun(job, runtime).run()
        } catch (any: Exception) {
            // Already reported by the listeners
            logger.debug("[job][run]{} Failure", key, any)
        } finally {
            runtime.running.set(null)
            runtime.progress.set(null)
            // Clears any interruption by a stop, before the thread goes back to Quartz
            Thread.interrupted()
        }
    }

    private fun monitoredRun(job: Job, runtime: JobRuntime): Runnable {
        val key = job.key
        val runListener = JobRunListener { progress ->
            jobListener.onJobProgress(key, progress)
            logger.debug("[job][progress]{} {}", key, progress.text)
            runtime.progress.set(progress)
        }
        val decoratedTask = jobDecorator.decorate(job) { job.task.run(runListener) }
        val recorded = MonitoredRun(decoratedTask, object : MonitoredRunListenerAdapter() {
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
                val end = Time.now
                start?.let {
                    meterRegistry.timer(
                        JobMetrics.ontrack_job_duration,
                        listOf(
                            Tag.of(JobMetrics.tag_job_category, key.type.category.key),
                            Tag.of(JobMetrics.tag_job_type, key.type.key),
                            Tag.of(JobMetrics.tag_job_key, key.id),
                        )
                    ).record(Duration.between(it, end))
                    try {
                        jobHistoryRecorder.record(jobKey = key, startedAt = it, endedAt = end, error = error)
                    } catch (any: Exception) {
                        logger.error("[job][run]$key Cannot record the run in the history", any)
                    }
                }
            }
        })
        return MonitoredRun(recorded, object : MonitoredRunListener {
            override fun onStart() {
                logger.debug("[job][task]{} On start", key)
                runtime.lastRunDate.set(Time.now())
                runtime.runCount.incrementAndGet()
                jobListener.onJobStart(key)
            }

            override fun onSuccess(duration: Long) {
                runtime.lastRunDurationMs.set(duration)
                logger.debug("[job][task]{} Success in {} ms", key, duration)
                jobListener.onJobEnd(key, duration)
                runtime.lastErrorCount.set(0)
                runtime.lastError.set(null)
                runtime.lastTimeoutCount.set(0)
            }

            override fun onFailure(ex: Exception) {
                runtime.lastErrorCount.incrementAndGet()
                runtime.lastError.set(ex.message)
                // A run stopped by its timeout fails, and keeps its timeout counted
                if (runtime.running.get()?.timedOut != true) {
                    runtime.lastTimeoutCount.set(0)
                }
                logger.debug("[job][task]{} Failure: {}", key, ex.message)
                try {
                    jobListener.onJobError(status(key) ?: return, ex)
                } catch (uncaught: Exception) {
                    logger.error("[job][task]$key Could not process error for job because of:", uncaught)
                    logger.error("[job][task]$key Initial error for job:", ex)
                }
            }

            override fun onCompletion() {
                logger.debug("[job][task]{} Job completed.", key)
                jobListener.onJobComplete(key)
            }
        })
    }

    override fun stop(key: JobKey): Boolean {
        if (jobRegistry.find(key) == null && jobRecordRepository.findByKey(name, key) == null) {
            throw JobNotScheduledException(key)
        }
        return interrupt(key)
    }

    private fun interrupt(key: JobKey): Boolean {
        val running = runtimes[key]?.running?.get() ?: return false
        logger.info("[job]{} Stopping the job", key)
        running.thread.interrupt()
        return true
    }

    override fun checkForTimeouts(): Int = runtimes.count { (key, runtime) ->
        val running = runtime.running.get()
        val timeout = jobRegistry.find(key)?.job?.timeout ?: ontrackConfigProperties.jobs.timeout
        if (running != null && System.currentTimeMillis() - running.start >= timeout.toMillis()) {
            logger.info("[job][timeout]{} Timeout - stopping the job", key)
            running.timedOut = true
            interrupt(key)
            runtime.lastTimeoutCount.incrementAndGet()
            true
        } else {
            false
        }
    }

    // ============================================================================================
    // Fires
    // ============================================================================================

    override fun fireImmediately(jobKey: JobKey): Optional<CompletableFuture<*>> {
        checkStarted()
        val registration = registration(jobKey)
        if (runtimes[jobKey]?.running?.get() != null) {
            logger.debug("[job][run]{} Not fired because already running", jobKey)
            return Optional.empty()
        }
        val job = registration.job
        if (!job.isValid || job.isDisabled) {
            logger.debug("[job][run]{} Not fired because not valid or disabled", jobKey)
            return Optional.empty()
        }
        val triggerKey = TriggerKey("$FIRE_PREFIX${UUID.randomUUID()}", QuartzSchedules.group(jobKey))
        return Optional.of(fire(registration, triggerKey, Instant.now()) ?: error("Fire $triggerKey not created"))
    }

    override fun fireLater(jobKey: JobKey, delay: Duration): Optional<CompletableFuture<*>> {
        checkStarted()
        val registration = registration(jobKey)
        val triggerKey = TriggerKey("$LATER_PREFIX${jobKey.id}", QuartzSchedules.group(jobKey))
        return Optional.ofNullable(fire(registration, triggerKey, Instant.now().plus(delay)))
    }

    /**
     * Creates a one-shot trigger for a job, which fires even when the job or all the jobs are
     * paused.
     *
     * @return Future completed once the run is over, or `null` if the trigger already exists
     */
    private fun fire(registration: JobRegistration, triggerKey: TriggerKey, at: Instant): CompletableFuture<*>? {
        val key = registration.job.key
        // Registered before the trigger, which may fire at once. A fire of the same trigger already
        // requested here is pending.
        val fire = PendingFire()
        if (pendingFires.putIfAbsent(triggerKey, fire) != null) {
            return null
        }
        val created = try {
            writeTransaction.execute {
                ensure(registration)
                if (scheduler.checkExists(triggerKey)) {
                    false
                } else {
                    scheduler.scheduleJob(
                        TriggerBuilder.newTrigger()
                            .withIdentity(triggerKey)
                            .forJob(QuartzSchedules.jobKey(key))
                            .startAt(Date.from(at))
                            .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                            .build()
                    )
                    if (scheduler.getTriggerState(triggerKey) == Trigger.TriggerState.PAUSED) {
                        scheduler.resumeTrigger(triggerKey)
                    }
                    true
                }
            } ?: false
        } catch (any: Exception) {
            pendingFires.remove(triggerKey, fire)
            throw any
        }
        return if (created) {
            logger.debug("[job][run]{} Fired at {}", key, at)
            fire.created = true
            fire.future
        } else {
            pendingFires.remove(triggerKey, fire)
            null
        }
    }

    private fun registration(key: JobKey): JobRegistration =
        jobRegistry.find(key)
            ?: run {
                jobRegistry.refresh()
                jobRegistry.find(key)
            }
            ?: throw JobNotScheduledException(key)

    // ============================================================================================
    // Pause & resume
    // ============================================================================================

    override fun pause() {
        checkStarted()
        writeTransaction.executeWithoutResult { scheduler.pauseAll() }
    }

    override fun resume() {
        checkStarted()
        writeTransaction.executeWithoutResult {
            scheduler.resumeAll()
            // Resuming all the jobs resumes all the triggers: the jobs paused on their own stay paused
            jobRecordRepository.findAll(name).filter { it.paused }.forEach { record ->
                scheduler.pauseJob(QuartzSchedules.jobKey(record.key))
            }
        }
    }

    override fun isPaused(): Boolean =
        started && scheduler.pausedTriggerGroups.contains(Constants.ALL_GROUPS_PAUSED)

    override fun pause(key: JobKey): Boolean {
        checkStarted()
        writeTransaction.executeWithoutResult {
            val record = jobRecordRepository.findByKey(name, key) ?: throw JobNotScheduledException(key)
            jobRecordRepository.setPaused(name, record.id, true)
            scheduler.pauseJob(QuartzSchedules.jobKey(key))
        }
        return true
    }

    override fun resume(key: JobKey): Boolean {
        checkStarted()
        writeTransaction.executeWithoutResult {
            val record = jobRecordRepository.findByKey(name, key) ?: throw JobNotScheduledException(key)
            jobRecordRepository.setPaused(name, record.id, false)
            // While all the jobs are paused, the job resumes with them
            if (!scheduler.pausedTriggerGroups.contains(Constants.ALL_GROUPS_PAUSED)) {
                scheduler.resumeJob(QuartzSchedules.jobKey(key))
            }
        }
        return true
    }

    override fun unschedule(key: JobKey): Boolean {
        checkStarted()
        val deleted = writeTransaction.execute {
            val record = jobRecordRepository.findByKey(name, key)
            if (record != null) {
                deleteQuartzJob(QuartzSchedules.jobKey(key))
                jobRecordRepository.delete(name, record.id)
                true
            } else {
                false
            }
        } ?: false
        interrupt(key)
        return deleted
    }

    // ============================================================================================
    // Statuses
    // ============================================================================================

    override fun getJobStatus(key: JobKey): Optional<JobStatus> = Optional.ofNullable(status(key))

    private fun status(key: JobKey): JobStatus? =
        jobRecordRepository.findByKey(name, key)?.let { toStatus(it) }

    override fun getAllJobKeys(): Collection<JobKey> = jobRecordRepository.findAll(name).map { it.key }

    override fun getJobKeysOfType(type: JobType): Collection<JobKey> =
        allJobKeys.filter { it.sameType(type) }.toSet()

    override fun getJobKeysOfCategory(category: JobCategory): Collection<JobKey> =
        allJobKeys.filter { it.sameCategory(category) }.toSet()

    override fun getJobStatuses(): Collection<JobStatus> =
        jobRecordRepository.findAll(name).map { toStatus(it) }

    override fun getJobKey(id: Long): Optional<JobKey> =
        Optional.ofNullable(
            id.takeIf { it in 1..Int.MAX_VALUE }
                ?.let { jobRecordRepository.findById(name, it.toInt()) }
                ?.key
        )

    /**
     * Status of a job of the cluster: its ID, schedule and pause from `JOBS` and Quartz, its
     * description and validity from this replica's registry when it knows it, its runs from this
     * replica.
     */
    private fun toStatus(record: JobRecord): JobStatus {
        val job = jobRegistry.find(record.key)?.job
        val key = job?.key ?: record.key
        val runtime = runtimes[key]
        val valid = job?.let { isValid(it) } ?: true
        return JobStatus(
            id = record.id.toLong(),
            key = key,
            schedule = record.schedule,
            actualSchedule = QuartzSchedules.actualSchedule(key, record.schedule, scattering),
            description = job?.description ?: record.description,
            isRunning = runtime?.running?.get() != null,
            isValid = valid,
            isPaused = record.paused,
            isDisabled = job?.isDisabled ?: false,
            progress = runtime?.progress?.get(),
            runCount = runtime?.runCount?.get() ?: 0,
            lastRunDate = runtime?.lastRunDate?.get(),
            lastRunDurationMs = runtime?.lastRunDurationMs?.get() ?: 0,
            nextRunDate = record.nextFireTime?.takeIf { valid },
            lastErrorCount = runtime?.lastErrorCount?.get() ?: 0,
            lastTimeoutCount = runtime?.lastTimeoutCount?.get() ?: 0,
            lastError = runtime?.lastError?.get(),
        )
    }

    private fun isValid(job: Job): Boolean =
        try {
            job.isValid
        } catch (any: Exception) {
            logger.debug("[job]{} Cannot check the validity of the job", job.key, any)
            false
        }

    /**
     * Fire requested by this replica, until its run is over
     */
    private class PendingFire {
        val future = CompletableFuture<Any?>()

        /**
         * Is its trigger created? Until then, its trigger not existing does not mean that it has run.
         */
        @Volatile
        var created = false
    }

    /**
     * Run of a job on this replica
     */
    private class RunningJob(
        val thread: Thread,
        val start: Long,
    ) {
        /**
         * Stopped because of its timeout
         */
        @Volatile
        var timedOut: Boolean = false
    }

    /**
     * Runs and counters of a job on this replica
     */
    private class JobRuntime {
        val running = AtomicReference<RunningJob?>()
        val progress = AtomicReference<JobRunProgress?>()
        val runCount = AtomicLong()
        val lastRunDate = AtomicReference<LocalDateTime?>()
        val lastRunDurationMs = AtomicLong()
        val lastErrorCount = AtomicLong()
        val lastTimeoutCount = AtomicLong()
        val lastError = AtomicReference<String?>()
    }

    companion object {
        /**
         * Prefix of the names of the triggers of the fires of "run now"
         */
        private const val FIRE_PREFIX = "run-now:"

        /**
         * Prefix of the name of the trigger of the delayed fire of a job, unique per job
         */
        private const val LATER_PREFIX = "later:"

        private const val FIRE_PREFIX_LENGTH = 8

        /**
         * Interval between two checks of the fires requested here and run elsewhere
         */
        private const val SWEEP_INTERVAL_MS = 2_000L
    }
}
