package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.query.FindingExposedForView
import net.nemerosa.ontrack.extension.findings.query.FindingQueryService
import org.springframework.graphql.execution.BatchLoaderRegistry
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * The batching behind `Finding.exposedFor`: a page of findings loads the periods of all of them in
 * one query per project ([FindingQueryService.getFindingsExposedFor]) rather than one per finding.
 *
 * Registered as a **mapped** batch loader: a finding the user cannot see is left out of the answer,
 * and resolves to `null`.
 */
@Component
class FindingExposedForDataLoader(
    registry: BatchLoaderRegistry,
    private val findingQueryService: FindingQueryService,
) {

    init {
        registry.forName<FindingExposedForKey, FindingExposedForView>(NAME)
            // On the thread of the GraphQL execution, which carries the security context of the caller
            .registerMappedBatchLoader { keys, _ -> Mono.fromCallable { load(keys) } }
    }

    /**
     * How long each finding has been exposed, one call per branch asked for.
     */
    fun load(keys: Set<FindingExposedForKey>): Map<FindingExposedForKey, FindingExposedForView> =
        keys.groupBy { it.branch }.flatMap { (branch, branchKeys) ->
            val views = findingQueryService.getFindingsExposedFor(branchKeys.map { it.finding }, branch)
            branchKeys.mapNotNull { key -> views[key.finding.id]?.let { key to it } }
        }.toMap()

    companion object {
        /**
         * The name the data fetcher of the field asks for the loader under
         */
        const val NAME = "findingExposedFor"
    }
}

/**
 * A finding, and the branch whose periods count — `null` for the branches which count toward its
 * state in its project.
 */
data class FindingExposedForKey(
    val finding: Finding,
    val branch: String?,
)
