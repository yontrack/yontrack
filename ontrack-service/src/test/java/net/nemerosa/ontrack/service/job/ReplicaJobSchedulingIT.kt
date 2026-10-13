package net.nemerosa.ontrack.service.job

import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.it.jobs.TestJobSupplier
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.support.JobNotScheduledException
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A job of scope [JobScope.REPLICA] (#2078), supplied like any other job, runs on the local
 * scheduler of the replica: not in Quartz, not on the jobs page, no "run now".
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReplicaJobSchedulingIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var jobScheduler: JobScheduler

    @Autowired
    private lateinit var testJobSupplier: TestJobSupplier

    @Autowired
    private lateinit var replicaJobScheduler: ReplicaJobScheduler

    private val scheduled = mutableListOf<JobKey>()

    @AfterEach
    fun cleanup() {
        scheduled.forEach { testJobSupplier.unschedule(jobScheduler, it) }
    }

    private class CountingJob : Job {
        val runs = AtomicInteger()
        private val key = JobCategory.of("test").withName("Test").getType("replica").withName("Replica")
            .getKey(uid("j-"))

        override fun getKey(): JobKey = key
        override fun getTask() = JobRun { runs.incrementAndGet() }
        override fun getDescription(): String = "Counting job ${key.id}"
        override fun isDisabled(): Boolean = false
    }

    private fun schedule(job: Job, schedule: Schedule, scope: JobScope): Job {
        testJobSupplier.schedule(jobScheduler, job, schedule, scope)
        scheduled += job.key
        return job
    }

    private fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                fail(message)
            }
            Thread.sleep(50)
        }
    }

    @Test
    fun `A replica job runs on the replica, and is not a job of the cluster`() {
        val job = CountingJob()
        schedule(job, Schedule(0, 100, TimeUnit.MILLISECONDS), JobScope.REPLICA)
        waitUntil("The replica job runs") { job.runs.get() >= 2 }
        assertTrue(job.key in replicaJobScheduler.keys, "Scheduled on the replica")
        assertFalse(job.key in jobScheduler.allJobKeys, "Not a job of the cluster")
        assertTrue(jobScheduler.jobStatuses.none { it.key == job.key }, "Not on the jobs page")
        assertTrue(jobScheduler.getJobStatus(job.key).isEmpty, "No status")
        assertFailsWith<JobNotScheduledException>("No run now") {
            jobScheduler.fireImmediately(job.key)
        }
    }

    @Test
    fun `A replica job no longer supplied stops running`() {
        val job = CountingJob()
        schedule(job, Schedule(0, 100, TimeUnit.MILLISECONDS), JobScope.REPLICA)
        waitUntil("The replica job runs") { job.runs.get() >= 1 }
        testJobSupplier.unschedule(jobScheduler, job.key)
        assertFalse(job.key in replicaJobScheduler.keys, "No longer scheduled on the replica")
        Thread.sleep(300)
        val runs = job.runs.get()
        Thread.sleep(500)
        assertEquals(runs, job.runs.get(), "No longer run")
    }

    @Test
    fun `A job moved from the cluster to the replica leaves the cluster`() {
        val job = CountingJob()
        schedule(job, Schedule.everyMinutes(60).after(60), JobScope.CLUSTER)
        assertTrue(job.key in jobScheduler.allJobKeys, "A job of the cluster")
        schedule(job, Schedule(0, 100, TimeUnit.MILLISECONDS), JobScope.REPLICA)
        assertFalse(job.key in jobScheduler.allJobKeys, "No longer a job of the cluster")
        waitUntil("The replica job runs") { job.runs.get() >= 1 }
    }
}
