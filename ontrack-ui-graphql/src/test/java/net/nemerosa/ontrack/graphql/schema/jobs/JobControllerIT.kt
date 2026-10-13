package net.nemerosa.ontrack.graphql.schema.jobs

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.it.jobs.OtherReplica
import net.nemerosa.ontrack.it.jobs.TestJobSupplier
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The status of a job and its "run now" through GraphQL, whichever replica runs the job (#2079).
 * Another replica is played by [OtherReplica]: not transactional, Quartz reading its own tables.
 */
@AsAdminTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JobControllerIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var jobScheduler: JobScheduler

    @Autowired
    private lateinit var testJobSupplier: TestJobSupplier

    @Test
    fun `A job running on another replica is shown running there, and is not launched again`() {
        withJob { key, id ->
            OtherReplica(namedParameterJdbcTemplate).use { other ->
                val since = System.currentTimeMillis() - 10_000
                other.run(key, since)
                run(
                    """
                        {
                            jobs(category: "${key.type.category.key}", type: "${key.type.key}") {
                                pageItems {
                                    isRunning
                                    state
                                    runningOn
                                    runningSince
                                    canRun
                                    canBeStopped
                                }
                            }
                        }
                    """
                ) { data ->
                    val job = data.path("jobs").path("pageItems").path(0)
                    assertTrue(job.path("isRunning").asBoolean(), "Running")
                    assertEquals("RUNNING", job.path("state").asText())
                    assertEquals(other.instance, job.path("runningOn").asText(), "Running on the other replica")
                    assertEquals(Time.store(Time.from(since)), job.path("runningSince").asText(), "Running since its fire")
                    assertEquals(false, job.path("canRun").asBoolean(), "Cannot run")
                    assertEquals(true, job.path("canBeStopped").asBoolean(), "Can be stopped")
                }
                run(
                    """
                        mutation {
                            launchJob(id: $id) {
                                ok
                                error
                                launchStatus
                            }
                        }
                    """
                ) { data ->
                    val result = data.path("launchJob")
                    assertEquals(false, result.path("ok").asBoolean(), "Not launched")
                    assertEquals("ALREADY_RUNNING", result.path("launchStatus").asText())
                    assertEquals(
                        "The job with ID = $id is already running on ${other.instance}",
                        result.path("error").asText()
                    )
                }
            }
        }
    }

    @Test
    fun `A job not running is launched`() {
        withJob { _, id ->
            run(
                """
                    mutation {
                        launchJob(id: $id) {
                            ok
                            error
                            launchStatus
                        }
                    }
                """
            ) { data ->
                val result = data.path("launchJob")
                assertEquals(true, result.path("ok").asBoolean(), "Launched")
                assertEquals("TRIGGERED", result.path("launchStatus").asText())
                assertTrue(result.path("error").isNull, "No error")
            }
        }
    }

    private fun withJob(code: (key: JobKey, id: Long) -> Unit) {
        val key = JobCategory.of("test").withName("Test").getType("controller").withName("Controller").getKey(uid("j-"))
        val job = object : Job {
            override fun getKey(): JobKey = key
            override fun getTask() = JobRun {}
            override fun getDescription(): String = "Test job"
            override fun isDisabled(): Boolean = false
        }
        testJobSupplier.schedule(jobScheduler, job, Schedule.NONE)
        try {
            val id = jobScheduler.getJobStatus(key).getOrNull()?.id ?: error("Job not scheduled")
            code(key, id)
        } finally {
            testJobSupplier.unschedule(jobScheduler, key)
        }
    }
}
