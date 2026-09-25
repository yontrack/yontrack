package net.nemerosa.ontrack.service.search

import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.orchestrator.JobOrchestratorSupplier
import net.nemerosa.ontrack.model.structure.SearchDocumentIndexer
import org.springframework.stereotype.Component

/**
 * One reconciliation job per [indexer][SearchDocumentIndexer], rebuilding its documents: manual
 * only by default, scheduled for the indexers which declare a schedule.
 */
@Component
class SearchDocumentIndexationJobs(
    private val searchDocumentIndexers: List<SearchDocumentIndexer>,
    private val searchDocumentService: SearchDocumentServiceImpl,
) : JobOrchestratorSupplier {

    companion object {
        val jobCategory = JobCategory("search", "Search")
        val rebuildJobType: JobType = jobCategory.getType("rebuild").withName("Search documents rebuild")
    }

    override val jobRegistrations: Collection<JobRegistration>
        get() = searchDocumentIndexers.map { indexer ->
            JobRegistration(
                job = RebuildJob(indexer),
                schedule = indexer.indexerSchedule,
            )
        }

    private inner class RebuildJob(
        private val indexer: SearchDocumentIndexer,
    ) : Job {

        override fun isDisabled(): Boolean = false

        override fun getKey(): JobKey = rebuildJobType.getKey(indexer.searchResultType.id)

        override fun getDescription(): String = "Rebuild of the search documents: ${indexer.indexerName}"

        override fun getTask() = JobRun { listener ->
            listener.message("Rebuilding the search documents of ${indexer.indexerName}")
            searchDocumentService.rebuild(indexer)
        }
    }

}
