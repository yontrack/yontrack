package net.nemerosa.ontrack.extension.scm.catalog.metrics

import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalogProjectFilterLink

/**
 * Counts of the SCM catalog, computed by [SCMCatalogMetricsJob] on one replica, and read by the
 * [gauges][SCMCatalogMetrics] of every replica (#2078): they are kept in the database.
 */
interface SCMCatalogMetricsStore {

    /**
     * Last counts computed, empty when never computed
     */
    fun counts(): Map<SCMCatalogProjectFilterLink, Int>

    /**
     * Stores the counts
     */
    fun store(counts: Map<SCMCatalogProjectFilterLink, Int>)

}
