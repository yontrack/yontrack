package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.query.FindingExposureView
import net.nemerosa.ontrack.extension.findings.query.FindingQueryService
import org.springframework.graphql.execution.BatchLoaderRegistry
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * The batching behind `Finding.exposures`: findings across projects, one external ID in many of
 * them, have their exposures loaded per project ([FindingQueryService.getFindingsExposures]) rather
 * than per finding, which would list the branches of a project again for each of its findings.
 *
 * Every finding gets an answer, the field being a list which is never null: an empty one for a
 * finding the user cannot see, or without any exposure on an enabled branch.
 */
@Component
class FindingExposuresDataLoader(
    registry: BatchLoaderRegistry,
    private val findingQueryService: FindingQueryService,
) {

    init {
        registry.forName<Finding, List<FindingExposureView>>(NAME)
            // On the thread of the GraphQL execution, which carries the security context of the caller
            .registerMappedBatchLoader { keys, _ -> Mono.fromCallable { load(keys) } }
    }

    private fun load(keys: Set<Finding>): Map<Finding, List<FindingExposureView>> {
        val exposures = findingQueryService.getFindingsExposures(keys)
        return keys.associateWith { finding -> exposures[finding.id] ?: emptyList() }
    }

    companion object {
        /**
         * The name the data fetcher of the field asks for the loader under
         */
        const val NAME = "findingExposures"
    }
}
