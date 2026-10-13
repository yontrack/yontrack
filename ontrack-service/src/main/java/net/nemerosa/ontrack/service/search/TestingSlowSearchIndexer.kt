package net.nemerosa.ontrack.service.search

import net.nemerosa.ontrack.extension.support.CoreExtensionFeature
import net.nemerosa.ontrack.model.structure.SearchDocument
import net.nemerosa.ontrack.model.structure.SearchDocumentIndexer
import net.nemerosa.ontrack.model.structure.SearchResultType
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Slow type of search results, for the acceptance tests of the rebuilds across several replicas
 * (#2088): it has no documents, and its rebuild runs until it is stopped - at most [SLOW_DURATION] -
 * while a project whose name starts with [SLOW_PREFIX] exists. Its rebuild at startup, before any
 * such project exists, is immediate.
 *
 * Only registered when `ontrack.config.search.testing.enabled` is set: never in production.
 */
@Component
@ConditionalOnProperty(name = ["ontrack.config.search.testing.enabled"], havingValue = "true")
class TestingSlowSearchIndexer(
    private val structureService: StructureService,
) : SearchDocumentIndexer {

    override val searchResultType = SearchResultType(
        feature = CoreExtensionFeature.INSTANCE.featureDescription,
        id = TYPE,
        name = "Testing (slow)",
        description = "Slow type of search results, for the acceptance tests",
        order = Int.MAX_VALUE,
    )

    override fun indexAll(processor: (SearchDocument) -> Unit) {
        if (structureService.projectList.any { it.name.startsWith(SLOW_PREFIX) }) {
            // Interrupted by a stop of the rebuild job
            Thread.sleep(SLOW_DURATION.toMillis())
        }
    }

    companion object {
        const val TYPE = "testing-slow"
        const val SLOW_PREFIX = "search-testing-slow-"

        /**
         * Longest rebuild, if not stopped
         */
        val SLOW_DURATION: Duration = Duration.ofMinutes(10)
    }
}
