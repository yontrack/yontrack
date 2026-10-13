package net.nemerosa.ontrack.extension.scm.catalog.metrics

import net.nemerosa.ontrack.extension.scm.catalog.SCMCatalogProjectFilterLink
import net.nemerosa.ontrack.model.support.StorageService
import org.springframework.stereotype.Component

/**
 * Counts of the SCM catalog in `STORAGE`.
 */
@Component
class SCMCatalogMetricsStoreImpl(
    private val storageService: StorageService,
) : SCMCatalogMetricsStore {

    override fun counts(): Map<SCMCatalogProjectFilterLink, Int> =
        storageService.find(STORE, KEY, StoredCounts::class)?.counts ?: emptyMap()

    override fun store(counts: Map<SCMCatalogProjectFilterLink, Int>) {
        storageService.store(STORE, KEY, StoredCounts(counts))
    }

    data class StoredCounts(
        val counts: Map<SCMCatalogProjectFilterLink, Int>,
    )

    companion object {
        private const val STORE = "net.nemerosa.ontrack.extension.scm.catalog.metrics.SCMCatalogMetrics"
        private const val KEY = "counts"
    }
}
