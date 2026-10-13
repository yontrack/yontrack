package net.nemerosa.ontrack.extension.scm.catalog.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalogProjectFilterLink
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Gauges of the SCM catalog, read from the counts stored by [SCMCatalogMetricsJob] (#2078): the
 * same on every replica - aggregate them with `max`, not `sum`.
 */
@Component
@Transactional(readOnly = true)
class SCMCatalogMetrics(
        private val scmCatalogMetricsStore: SCMCatalogMetricsStore
) : MeterBinder {

    override fun bindTo(registry: MeterRegistry) {
        register(registry, ONTRACK_EXTENSION_SCM_CATALOG_ALL, SCMCatalogProjectFilterLink.ALL)
        register(registry, ONTRACK_EXTENSION_SCM_CATALOG_ENTRIES, SCMCatalogProjectFilterLink.ENTRY)
        register(registry, ONTRACK_EXTENSION_SCM_CATALOG_LINKED, SCMCatalogProjectFilterLink.LINKED)
        register(registry, ONTRACK_EXTENSION_SCM_CATALOG_UNLINKED, SCMCatalogProjectFilterLink.UNLINKED)
        register(registry, ONTRACK_EXTENSION_SCM_CATALOG_ORPHAN, SCMCatalogProjectFilterLink.ORPHAN)
    }

    private fun register(registry: MeterRegistry, name: String, link: SCMCatalogProjectFilterLink) {
        registry.gauge(name, scmCatalogMetricsStore) { it.counts()[link]?.toDouble() ?: 0.0 }
    }

    /**
     * List of metrics
     */
    companion object {
        const val ONTRACK_EXTENSION_SCM_CATALOG_ALL = "ontrack_extension_scm_catalog_total"
        const val ONTRACK_EXTENSION_SCM_CATALOG_ENTRIES = "ontrack_extension_scm_catalog_entries"
        const val ONTRACK_EXTENSION_SCM_CATALOG_LINKED = "ontrack_extension_scm_catalog_linked"
        const val ONTRACK_EXTENSION_SCM_CATALOG_UNLINKED = "ontrack_extension_scm_catalog_unlinked"
        const val ONTRACK_EXTENSION_SCM_CATALOG_ORPHAN = "ontrack_extension_scm_catalog_orphan"
    }
}