package net.nemerosa.ontrack.service.search

import net.nemerosa.ontrack.job.JobLaunchStatus
import net.nemerosa.ontrack.job.JobScheduler
import net.nemerosa.ontrack.json.toObject
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.security.ProjectList
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.repository.search.SearchDocumentHit
import net.nemerosa.ontrack.repository.search.SearchDocumentScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Search on the search documents stored in Postgres, as described by the
 * [indexers][SearchDocumentIndexer] (ADR 0017).
 *
 * A type is being rebuilt while its rebuild job, or its rebuild job at startup, runs on any replica:
 * read from the status of the jobs, the same on every replica (#2088). The rebuilds asked through
 * this service run as the rebuild job of their type.
 */
@Service
@Transactional(readOnly = true)
class SearchServiceImpl(
    searchDocumentIndexers: List<SearchDocumentIndexer>,
    private val searchDocumentService: SearchDocumentServiceImpl,
    private val securityService: SecurityService,
    private val structureService: StructureService,
    private val jobScheduler: JobScheduler,
) : SearchService {

    companion object {
        const val MESSAGE_INDEX_BEING_BUILT = "Search index is being built"

        /**
         * ID of no project. A project function granted for it is granted independently of the
         * project, so for all of them.
         */
        private const val NO_PROJECT_ID = 0

        /**
         * Interval between two attempts to launch a rebuild job which is already running
         */
        private const val RELAUNCH_INTERVAL_MS = 1_000L
    }

    private val logger: Logger = LoggerFactory.getLogger(SearchServiceImpl::class.java)

    /**
     * Indexers, per type.
     */
    private val indexers: Map<String, SearchDocumentIndexer> =
        searchDocumentIndexers.associateBy { it.searchResultType.id }

    override val searchResultTypes: List<SearchResultType>
        get() = indexers.values.map { it.searchResultType }.sortedBy { it.order }

    override fun search(request: SearchQueryRequest): SearchResults {
        // Types to search into, in their display order
        val requested = request.types?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.toSet()
        val types = searchResultTypes.filter { requested == null || it.id in requested }
        return searchDocuments(request, types)
            ?: SearchResults(
                items = emptyList(),
                offset = request.offset,
                total = 0,
                message = null,
            )
    }

    /**
     * Search on the search documents.
     *
     * @return `null` if the query is too short to be searched
     */
    private fun searchDocuments(request: SearchQueryRequest, types: List<SearchResultType>): SearchResults? {
        if (request.query.trim().length < SearchQueryRequest.MIN_QUERY_LENGTH) {
            return null
        } else if (types.isEmpty()) {
            return SearchResults(items = emptyList(), offset = request.offset, total = 0, message = null)
        }
        val scope = scope(types)
        val page = searchDocumentService.search(
            query = request.query,
            scope = scope,
            offset = request.offset,
            size = request.size,
            perType = request.perType,
            highlight = request.highlight,
        ) ?: return null
        val typesById = types.associateBy { it.id }
        val rebuilding = rebuildingTypes()
        // Current names of the projects and branches the documents found refer to
        val hits = SearchDocumentNames.resolve(
            hits = page.items,
            references = { type -> indexers[type]?.nameReferences ?: emptyList() },
            lookup = searchDocumentService::findReferenceNames,
            visible = scope::isProjectVisible,
        )
        return SearchResults(
            items = hits.mapNotNull { hit ->
                typesById[hit.type]?.let { type -> toSearchResult(hit, type) }
            },
            offset = if (request.perType != null) 0 else request.offset,
            total = page.total,
            message = if (types.any { it.id in rebuilding }) MESSAGE_INDEX_BEING_BUILT else null,
            facets = types.mapNotNull { type ->
                page.facets[type.id]?.let { facet -> SearchFacet(type, facet.count, facet.capped) }
            },
        )
    }

    /**
     * Types being rebuilt, on any replica: those whose rebuild job, or rebuild job at startup, runs.
     */
    private fun rebuildingTypes(): Set<String> =
        jobScheduler.getJobStatusesOfCategory(SearchDocumentIndexationJobs.jobCategory)
            .filter { status ->
                status.isRunning && (
                        status.key.sameType(SearchDocumentIndexationJobs.rebuildJobType) ||
                                status.key.sameType(SearchDocumentStartupRebuild.startupRebuildJobType)
                        )
            }
            .map { it.key.id }
            .toSet()

    /**
     * What the current user can see, for the given types.
     */
    private fun scope(types: List<SearchResultType>): SearchDocumentScope {
        val allProjects = securityService.isGlobalFunctionGranted(ProjectList::class.java)
        val projectIds = if (!allProjects && securityService.isLogged) {
            structureService.projectList.map { it.id() }
        } else {
            emptyList()
        }
        val ids = types.map { it.id }
        return SearchDocumentScope(
            types = ids,
            allProjects = allProjects,
            projectIds = projectIds,
            projectLessTypes = ids.filter { type ->
                indexers[type]?.globalFunction?.let { securityService.isGlobalFunctionGranted(it) } ?: false
            },
            restrictedTypes = restrictedTypes(ids, allProjects, projectIds),
            nonFuzzyTypes = ids.filter { type -> indexers[type]?.fuzzyMatching == false },
        )
    }

    /**
     * Types whose indexer declares a [project function][SearchDocumentIndexer.projectFunction], with
     * the visible projects where this function is granted — unless it is granted in all of them.
     */
    private fun restrictedTypes(ids: List<String>, allProjects: Boolean, projectIds: List<Int>): Map<String, List<Int>> {
        val functions = ids.mapNotNull { type ->
            indexers[type]?.projectFunction
                // Granted independently of the project: a global role, or the administrator
                ?.takeIf { !securityService.isProjectFunctionGranted(NO_PROJECT_ID, it) }
                ?.let { type to it }
        }
        if (functions.isEmpty()) return emptyMap()
        val visible = if (allProjects) structureService.projectList.map { it.id() } else projectIds
        return functions.mapNotNull { (type, function) ->
            val granted = visible.filter { securityService.isProjectFunctionGranted(it, function) }
            if (granted.size == visible.size) {
                null
            } else {
                type to granted
            }
        }.toMap()
    }

    @Suppress("UNCHECKED_CAST")
    private fun toSearchResult(hit: SearchDocumentHit, type: SearchResultType) = SearchResult(
        title = hit.title,
        description = hit.text ?: "",
        accuracy = hit.score,
        type = type,
        data = hit.data.toObject() as? Map<String, *>,
        highlight = hit.highlight,
    )

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    override fun indexReset(reindex: Boolean, logErrors: Boolean): Ack {
        val ok = indexers.values.all { indexer ->
            try {
                searchDocumentService.clear(indexer.searchResultType.id)
                if (reindex) {
                    rebuild(indexer)
                }
                true
            } catch (any: Exception) {
                if (logErrors) {
                    logger.error("[search][${indexer.searchResultType.id}] Cannot reset the search documents", any)
                    false
                } else {
                    throw any
                }
            }
        }
        return Ack(ok)
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    override fun reindex(resultType: String) {
        val indexer = indexers[resultType] ?: throw SearchResultTypeNotFoundException(resultType)
        rebuild(indexer)
    }

    /**
     * Runs the rebuild job of a type, on any replica, and waits for the end of its run: one rebuild
     * of a type at a time in the cluster, which the status of the job shows. A run already in
     * progress is waited for before launching another one, which sees all the changes made before
     * this call.
     *
     * @throws SearchIndexRebuildException If the rebuild failed
     */
    private fun rebuild(indexer: SearchDocumentIndexer) {
        val type = indexer.searchResultType.id
        val key = SearchDocumentIndexationJobs.rebuildJobType.getKey(type)
        while (true) {
            val launch = jobScheduler.launch(key)
            when (launch.status) {
                JobLaunchStatus.TRIGGERED -> {
                    launch.future?.get()
                    break
                }

                JobLaunchStatus.ALREADY_RUNNING -> Thread.sleep(RELAUNCH_INTERVAL_MS)

                JobLaunchStatus.NOT_RUNNABLE -> throw SearchIndexRebuildException(type, "its rebuild job cannot run")
            }
        }
        jobScheduler.getJobStatus(key).orElse(null)?.lastError?.let { error ->
            throw SearchIndexRebuildException(type, error)
        }
    }

}
