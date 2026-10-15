package net.nemerosa.ontrack.service.job

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.it.jobs.TestJobSupplier
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.support.JobHistoryRecorder
import net.nemerosa.ontrack.repository.JobRecordRepository
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.quartz.Scheduler
import org.quartz.TriggerKey
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals

/**
 * Several replicas, each with its [QuartzJobScheduler], sharing the Quartz store of the database.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QuartzJobSchedulerReplicasIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var jobScheduler: JobScheduler

    @Autowired
    private lateinit var testJobSupplier: TestJobSupplier

    @Autowired
    private lateinit var jobRegistry: JobRegistry

    @Autowired
    private lateinit var jobRecordRepository: JobRecordRepository

    @Autowired
    private lateinit var scheduler: Scheduler

    @Autowired
    private lateinit var jobSchedulerName: JobSchedulerName

    @Autowired
    private lateinit var jobDecorator: JobDecorator

    @Autowired
    private lateinit var jobListener: JobListener

    @Autowired
    private lateinit var jobHistoryRecorder: JobHistoryRecorder

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val scheduled = mutableListOf<JobKey>()

    @AfterEach
    fun cleanup() {
        scheduled.forEach { testJobSupplier.unschedule(jobScheduler, it) }
    }

    private class IdleJob : Job {
        private val key = JobCategory.of("test").withName("Test").getType("replicas").withName("Replicas")
            .getKey(uid("j-"))

        override fun getKey(): JobKey = key
        override fun getTask() = JobRun { }
        override fun getDescription(): String = "Idle job ${key.id}"
        override fun isDisabled(): Boolean = false
    }

    /**
     * A replica whose checks of the existence of a trigger all wait at the [barrier]: no replica
     * schedules the trigger before every replica has checked that it does not exist yet.
     */
    private fun replica(barrier: CyclicBarrier): QuartzJobScheduler {
        val racing = Proxy.newProxyInstance(
            Scheduler::class.java.classLoader,
            arrayOf(Scheduler::class.java),
        ) { _, method, args ->
            if (method.name == "checkExists" && args?.singleOrNull() is TriggerKey) {
                barrier.await(10, TimeUnit.SECONDS)
            }
            try {
                method.invoke(scheduler, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        } as Scheduler
        val provider = DefaultListableBeanFactory().apply { registerSingleton("scheduler", racing) }
            .getBeanProvider(Scheduler::class.java)
        return QuartzJobScheduler(
            jobRegistry = jobRegistry,
            jobRecordRepository = jobRecordRepository,
            schedulerProvider = provider,
            jobSchedulerName = jobSchedulerName,
            jobDecorator = jobDecorator,
            jobListener = jobListener,
            jobHistoryRecorder = jobHistoryRecorder,
            meterRegistry = SimpleMeterRegistry(),
            ontrackConfigProperties = ontrackConfigProperties,
            transactionManager = transactionManager,
        ).apply {
            // Started without reconciling the cluster again
            QuartzJobScheduler::class.java.getDeclaredField("started").apply { isAccessible = true }.set(this, true)
        }
    }

    @Test
    fun `Two replicas firing the same job later at once - one fire, no error`() {
        val job = IdleJob()
        testJobSupplier.schedule(jobScheduler, job, Schedule.NONE)
        scheduled += job.key
        val barrier = CyclicBarrier(2)
        val replicas = listOf(replica(barrier), replica(barrier))
        val executor = Executors.newFixedThreadPool(2)
        try {
            val fires = replicas
                .map { replica -> executor.submit<Any?> { replica.fireLater(job.key, Duration.ofHours(1)).getOrNull() } }
                .map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, fires.count { it != null }, "One replica fires the job, the other one gets no fire")
        } finally {
            executor.shutdownNow()
        }
    }
}
