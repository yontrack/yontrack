package net.nemerosa.ontrack.extension.scm.catalog.metrics

import net.nemerosa.ontrack.extension.scm.SCMExtensionConfigProperties
import net.nemerosa.ontrack.extension.scm.SCMJobs
import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalogFilterService
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.model.support.JobProvider
import org.springframework.stereotype.Component

/**
 * Indexes the entries of the SCM catalog, and computes its counts, once a day in the cluster: the
 * counts are stored in the database, where the gauges of every replica read them (#2078).
 */
@Component
class SCMCatalogMetricsJob(
    private val scmCatalogMetricsStore: SCMCatalogMetricsStore,
    private val scmCatalogFilterService: SCMCatalogFilterService,
    private val scmExtensionConfigProperties: SCMExtensionConfigProperties,
) : JobProvider {
    override fun getStartingJobs(): Collection<JobRegistration> =
        if (scmExtensionConfigProperties.catalog.enabled) {
            setOf(
                JobRegistration(
                    createSCMCatalogMetricsJob(),
                    Schedule.EVERY_DAY
                )
            )
        } else {
            emptySet()
        }

    private fun createSCMCatalogMetricsJob() = object : Job {

        override fun isDisabled(): Boolean = false

        override fun getKey(): JobKey = SCMJobs.category
            .getType("catalog-metrics").withName("Getting catalog metrics")
            .getKey("catalog-metrics")

        override fun getDescription(): String = "Collection of SCM Catalog metrics"

        override fun getTask() = JobRun {
            val counts = scmCatalogFilterService.indexCatalogProjectEntries()
            scmCatalogMetricsStore.store(counts)
        }

    }
}