package net.nemerosa.ontrack.extension.scm.catalog.metrics

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalogProjectFilterLink
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.support.StorageService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * The counts of the SCM catalog are computed by a cluster job, on one replica, and stored in the
 * database, from which the gauges of every replica read them (#2078).
 */
class SCMCatalogMetricsIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var storageService: StorageService

    @Autowired
    private lateinit var scmCatalogMetricsStore: SCMCatalogMetricsStore

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Test
    fun `Counts stored by one replica are read by another`() {
        val counts = mapOf(
            SCMCatalogProjectFilterLink.ALL to 10,
            SCMCatalogProjectFilterLink.ENTRY to 8,
            SCMCatalogProjectFilterLink.LINKED to 5,
            SCMCatalogProjectFilterLink.UNLINKED to 3,
            SCMCatalogProjectFilterLink.ORPHAN to 2,
        )
        scmCatalogMetricsStore.store(counts)
        // Another replica, sharing the database
        val other = SCMCatalogMetricsStoreImpl(storageService)
        assertEquals(counts, other.counts())
    }

    @Test
    fun `The gauges read the stored counts`() {
        scmCatalogMetricsStore.store(
            mapOf(
                SCMCatalogProjectFilterLink.ALL to 12,
                SCMCatalogProjectFilterLink.ORPHAN to 4,
            )
        )
        assertEquals(12.0, gauge(SCMCatalogMetrics.ONTRACK_EXTENSION_SCM_CATALOG_ALL))
        assertEquals(4.0, gauge(SCMCatalogMetrics.ONTRACK_EXTENSION_SCM_CATALOG_ORPHAN))
        assertEquals(0.0, gauge(SCMCatalogMetrics.ONTRACK_EXTENSION_SCM_CATALOG_LINKED))
    }

    private fun gauge(name: String): Double? = meterRegistry.find(name).gauge()?.value()
}
