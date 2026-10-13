package net.nemerosa.ontrack.boot.support

import net.nemerosa.ontrack.boot.ui.AbstractWebTestSupport
import net.nemerosa.ontrack.job.JobRegistration
import net.nemerosa.ontrack.job.JobScope
import net.nemerosa.ontrack.job.orchestrator.JobOrchestratorSupplier
import net.nemerosa.ontrack.model.support.JobProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * The jobs which run on every replica (#2078), of the core and of all the extensions.
 *
 * A `REPLICA` job refreshes the memory of its pod only: it is not on the jobs page, has no
 * history, no pause and no "run now". Only what is per pod by nature runs this way - what is the
 * same for the cluster is computed by a cluster job and stored in the database (ADR 0021). A new
 * one is a deliberate choice: add it to this list.
 */
class ReplicaJobsIT : AbstractWebTestSupport() {

    @Autowired
    private lateinit var jobProviders: List<JobProvider>

    @Autowired
    private lateinit var jobOrchestratorSuppliers: List<JobOrchestratorSupplier>

    @Test
    fun `List of the jobs running on every replica`() {
        val registrations: List<JobRegistration> = asAdmin {
            jobProviders.flatMap { it.startingJobs } +
                    jobOrchestratorSuppliers.flatMap { it.jobRegistrations }
        }
        val replicaJobs = registrations
            .filter { it.scope == JobScope.REPLICA }
            .map { registration ->
                val key = registration.job.key
                "${key.type.category.key}/${key.type.key}/${key.id}"
            }
            .sorted()
        assertEquals(
            listOf(
                // Gauges of the evidences, in the meter registry of each pod
                "audit-trail/evidence-metrics/metrics",
                // Reachability of the evidence storage from each pod
                "audit-trail/evidence-storage/probe",
                // Rate-limit gauges of the GitHub configurations, in the meter registry of each pod
                "github/rate-limit-metrics/gauges",
            ),
            replicaJobs,
        )
    }
}
