package net.nemerosa.ontrack.service.elasticsearch

import kotlinx.coroutines.future.await
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.orchestrator.JobOrchestratorSupplier
import net.nemerosa.ontrack.model.structure.SearchIndexService
import net.nemerosa.ontrack.model.structure.SearchIndexer
import net.nemerosa.ontrack.model.structure.SearchItem
import net.nemerosa.ontrack.service.elasticsearch.ElasticSearchJobs.indexationAllJobKey
import net.nemerosa.ontrack.service.elasticsearch.ElasticSearchJobs.indexationClearJobKey
import net.nemerosa.ontrack.service.elasticsearch.ElasticSearchJobs.indexationJobType
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * One job per type of search.
 */
@Component
class ElasticSearchIndexationJobs(
    private val applicationContext: ApplicationContext,
    private val elasticSearchService: SearchIndexService,
    private val searchService: ElasticSearchServiceImpl,
    private val jobScheduler: JobScheduler
) : JobOrchestratorSupplier {

    private val searchIndexers: List<SearchIndexer<*>> by lazy {
        applicationContext.getBeansOfType(SearchIndexer::class.java).values.toList()
    }

    override val jobRegistrations: Collection<JobRegistration>
        get() = searchIndexers.filter { indexer ->
            !indexer.isIndexationDisabled
        }.map { indexer ->
            createIndexationJobRegistration(indexer)
        } + createGlobalIndexationJob() + createClearIndexationJob()

    private fun createGlobalIndexationJob() = JobRegistration(
        schedule = Schedule.NONE,
        job = object : Job {
            override fun isDisabled(): Boolean = false

            override fun getKey(): JobKey = indexationAllJobKey

            override fun getDescription(): String = "All re-indexations"

            override fun getTask() = JobRun { listener ->
                listener.message("Launching all indexations")
                runBlocking {
                    val jobs = searchIndexers.filter { indexer ->
                        !indexer.isIndexationDisabled
                    }.mapNotNull { indexer ->
                        val key = indexationJobType.getKey(indexer.indexerId)
                        jobScheduler.fireImmediately(key).orElse(null)
                    }.map { stage ->
                        launch {
                            stage.await()
                        }
                    }
                    // Waits for all jobs to complete
                    withTimeout(TimeUnit.HOURS.toMillis(1)) {
                        jobs.joinAll()
                    }
                }
            }
        }
    )

    private fun createClearIndexationJob() = JobRegistration(
        schedule = Schedule.NONE,
        job = object : Job {
            override fun isDisabled(): Boolean = false

            override fun getKey(): JobKey = indexationClearJobKey

            override fun getDescription(): String = "Clear all search indexes"

            override fun getTask() = JobRun { listener ->
                listener.message("Clearing all search indexes...")
                searchService.indexReset(reindex = false, logErrors = false)
            }
        }
    )

    private fun <T : SearchItem> createIndexationJobRegistration(searchIndexer: SearchIndexer<T>) = JobRegistration(
        job = createIndexationJob(searchIndexer),
        schedule = searchIndexer.indexerSchedule
    )

    private fun <T : SearchItem> createIndexationJob(indexer: SearchIndexer<T>) = ElasticSearchIndexationJob(indexer)

    private inner class ElasticSearchIndexationJob<T : SearchItem>(
        private val indexer: SearchIndexer<T>,
    ) : Job {

        override fun isDisabled(): Boolean = false

        override fun getKey(): JobKey =
            indexationJobType.getKey(indexer.indexerId)

        override fun getDescription(): String = indexer.indexerName

        override fun getTask() = JobRun { listener ->
            listener.message("Launching indexation for ${indexer.indexerName}")
            elasticSearchService.index(indexer)
        }
    }

}