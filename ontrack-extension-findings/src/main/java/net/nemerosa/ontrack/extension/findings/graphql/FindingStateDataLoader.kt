package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.extension.findings.query.FindingQueryService
import org.springframework.graphql.execution.BatchLoaderRegistry
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * The batching behind `Finding.state`: findings across projects, one external ID in many of them,
 * have their states loaded per project ([FindingQueryService.getFindingStates]) rather than per
 * finding, which would list the branches of a project again for each of its findings.
 *
 * Registered as a **mapped** batch loader: a finding the user cannot see, or exposed only on
 * disabled branches, has no state, and resolves to `null`.
 */
@Component
class FindingStateDataLoader(
    registry: BatchLoaderRegistry,
    private val findingQueryService: FindingQueryService,
) {

    init {
        registry.forName<Finding, FindingState>(NAME)
            // On the thread of the GraphQL execution, which carries the security context of the caller
            .registerMappedBatchLoader { keys, _ -> Mono.fromCallable { load(keys) } }
    }

    private fun load(keys: Set<Finding>): Map<Finding, FindingState> {
        val states = findingQueryService.getFindingStates(keys)
        return keys.mapNotNull { finding -> states[finding.id]?.let { finding to it } }.toMap()
    }

    companion object {
        /**
         * The name the data fetcher of the field asks for the loader under
         */
        const val NAME = "findingState"
    }
}
