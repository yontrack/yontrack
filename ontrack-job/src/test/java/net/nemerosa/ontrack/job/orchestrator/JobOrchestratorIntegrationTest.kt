package net.nemerosa.ontrack.job.orchestrator

import io.mockk.mockk
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.support.AbstractJobTest
import net.nemerosa.ontrack.job.support.ConfigurableJob
import net.nemerosa.ontrack.test.assertNotPresent
import net.nemerosa.ontrack.test.assertPresent
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import java.util.stream.Stream
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JobOrchestratorIntegrationTest : AbstractJobTest() {

    @Test
    fun `Removed job`() {
        withOrchestrator {
            // Two jobs
            supplier += "1"
            supplier += "2"
            // Fires the orchestration and checks jobs
            orchestration {
                job("1") {
                    assertFalse(isDisabled)
                }
                job("2") {
                    assertFalse(isDisabled)
                }
            }
            // Removing a job from the supplier
            supplier -= "2"
            // Fires the orchestration and checks jobs
            orchestration {
                job("1") {
                    assertFalse(isDisabled)
                }
                noJob("2")
            }
        }
    }

    @Test
    fun `Added job`() {
        withOrchestrator {
            // Two jobs
            supplier += "1"
            supplier += "2"
            // Fires the orchestration and checks jobs
            orchestration {
                job("1") {
                    assertFalse(isDisabled)
                }
                job("2") {
                    assertFalse(isDisabled)
                }
            }
            // Adding a job to the supplier
            supplier += "3"
            // Fires the orchestration and checks jobs
            orchestration {
                job("1") {
                    assertFalse(isDisabled)
                }
                job("2") {
                    assertFalse(isDisabled)
                }
                job("3") {
                    assertFalse(isDisabled)
                }
            }
        }
    }

    @Test
    fun `Job status changed`() {
        withOrchestrator {
            // Two jobs
            supplier += "1"
            supplier += "2"
            // Fires the orchestration and checks jobs
            orchestration {
                job("1") {
                    assertFalse(isDisabled)
                }
                job("2") {
                    assertFalse(isDisabled)
                }
            }
            // Disables the job
            supplier += "2" to true
            // Fires the orchestration and checks jobs
            orchestration {
                job("1") {
                    assertFalse(isDisabled)
                }
                job("2") {
                    assertTrue(isDisabled, "The job should be marked as disabled.")
                }
            }
            // Enables the job
            supplier += "2" to false
            // Fires the orchestration and checks jobs
            orchestration {
                job("1") {
                    assertFalse(isDisabled)
                }
                job("2") {
                    assertFalse(isDisabled, "The job should be marked as enabled again.")
                }
            }
        }
    }

    private fun withOrchestrator(code: OrchestratorContext.() -> Unit) {
        // Supplier
        val supplier = TestJobOrchestratorSupplier()
        // OK
        scheduler {
            OrchestratorContext(scheduler, supplier).code()
        }
    }

    inner class OrchestratorContext(
            private val scheduler: JobScheduler,
            val supplier: TestJobOrchestratorSupplier
    ) {
        private val orchestrator: JobOrchestrator = JobOrchestrator(
                scheduler,
                "Test",
                listOf(supplier),
                mockk(relaxed = true)
        )

        fun orchestration(code: OrchestrationContext.() -> Unit) {
            // Registration of the orchestrator
            scheduler.schedule(orchestrator, Schedule.NONE)
            // Fires the orchestration
            scheduler.fireImmediately(orchestrator.key).orElseThrow { RuntimeException("Not scheduled") }
            jobPool.runUntilIdle()
            // Code
            OrchestrationContext(scheduler).code()
        }
    }

    class OrchestrationContext(
            private val jobScheduler: JobScheduler
    ) {
        fun job(name: String, code: JobStatus.() -> Unit) {
            val status = jobScheduler.getJobStatus(TestJobOrchestratorSupplier.key(name))
            assertPresent(status, "Job with key $name must be present.") {
                it.code()
            }
        }

        fun noJob(name: String) {
            val status = jobScheduler.getJobStatus(TestJobOrchestratorSupplier.key(name))
            assertNotPresent(status, "Job with key $name must not be present.")
        }
    }

    class TestJobOrchestratorSupplier : JobOrchestratorSupplier {

        private val jobs: MutableMap<String, Job> = mutableMapOf()

        override val jobRegistrations: Collection<JobRegistration> get() =
                jobs.values.map { JobRegistration.of(it).withSchedule(Schedule.EVERY_DAY) }

        operator fun plusAssign(name: String) {
            jobs[name] = ConfigurableJob(name)
        }

        operator fun plusAssign(state: Pair<String, Boolean>) {
            jobs[state.first] = ConfigurableJob(state.first, disabled = state.second)
        }

        operator fun minusAssign(name: String) {
            jobs.remove(name)
        }

        companion object {
            fun key(name: String): JobKey = ConfigurableJob(name).key
        }

    }

}