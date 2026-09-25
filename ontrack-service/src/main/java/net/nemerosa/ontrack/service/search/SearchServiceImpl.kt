package net.nemerosa.ontrack.service.search

import net.nemerosa.ontrack.json.toObject
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.security.ProjectList
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.repository.search.SearchDocumentHit
import net.nemerosa.ontrack.repository.search.SearchDocumentScope
import net.nemerosa.ontrack.service.elasticsearch.ElasticSearchServiceImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Search, routed per type while the indexers are migrated from Elasticsearch to Postgres: the
 * types having a [SearchDocumentIndexer] are searched on Postgres, the other ones on
 * Elasticsearch.
 *
 * The router goes once every indexer is migrated (#1882).
 */
@Service
@Transactional(readOnly = true)
class SearchServiceImpl(
    searchDocumentIndexers: List<SearchDocumentIndexer>,
    private val searchDocumentService: SearchDocumentServiceImpl,
    private val elasticSearchService: ElasticSearchServiceImpl,
    private val securityService: SecurityService,
    private val structureService: StructureService,
) : SearchService {

    companion object {
        const val MESSAGE_INDEX_BEING_BUILT = "Search index is being built"

        /**
         * ID of no project. A project function granted for it is granted independently of the
         * project, so for all of them.
         */
        private const val NO_PROJECT_ID = 0
    }

    private val logger: Logger = LoggerFactory.getLogger(SearchServiceImpl::class.java)

    /**
     * Indexers migrated to Postgres, per type.
     */
    private val indexers: Map<String, SearchDocumentIndexer> =
        searchDocumentIndexers.associateBy { it.searchResultType.id }

    override val searchResultTypes: List<SearchResultType>
        get() = (indexers.values.map { it.searchResultType } + elasticSearchService.searchResultTypes)
            .distinctBy { it.id }
            .sortedBy { it.order }

    override fun search(request: SearchQueryRequest): SearchResults {
        // Types to search into, in their display order
        val requested = request.types?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.toSet()
        val types = searchResultTypes.filter { requested == null || it.id in requested }
        val postgresTypes = types.filter { it.id in indexers }
        val elasticTypes = types.filter { it.id !in indexers }

        // Postgres
        val postgres = searchPostgres(request, postgresTypes)
            ?: return SearchResults(
                items = emptyList(),
                offset = request.offset,
                total = 0,
                message = null,
            )

        // Only Postgres types
        if (elasticTypes.isEmpty()) {
            return postgres
        }
        // One Elasticsearch type only, with a page: as before the migration
        if (postgresTypes.isEmpty() && elasticTypes.size == 1 && request.perType == null) {
            val type = elasticTypes.first()
            return elasticSearchService.paginatedSearch(
                type = type.id,
                token = request.query,
                offset = request.offset,
                size = request.size,
            ).let { results ->
                SearchResults(
                    items = results.items,
                    offset = results.offset,
                    total = results.total,
                    message = results.message,
                    facets = if (results.total > 0) listOf(SearchFacet(type, results.total)) else emptyList(),
                )
            }
        }
        // Mixed
        return if (request.perType != null) {
            mixedPerType(request, request.perType!!, postgres, elasticTypes)
        } else {
            mixedPage(request, postgres, elasticTypes)
        }
    }

    /**
     * Best results of each type: those of Postgres, then those of each Elasticsearch type.
     */
    private fun mixedPerType(
        request: SearchQueryRequest,
        perType: Int,
        postgres: SearchResults,
        elasticTypes: List<SearchResultType>,
    ): SearchResults {
        val items = postgres.items.toMutableList()
        val facets = postgres.facets.toMutableList()
        var total = postgres.total
        elasticTypes.forEach { type ->
            val results = elasticSearchService.paginatedSearch(type.id, request.query, 0, perType)
            items += results.items
            total += results.total
            if (results.total > 0) {
                facets += SearchFacet(type, results.total)
            }
        }
        return SearchResults(
            items = items,
            offset = 0,
            total = total,
            message = postgres.message,
            facets = facets,
        )
    }

    /**
     * Page over the concatenation of the Postgres results, then of the results of each
     * Elasticsearch type in their display order. Scores of the two backends cannot be compared.
     */
    private fun mixedPage(
        request: SearchQueryRequest,
        postgres: SearchResults,
        elasticTypes: List<SearchResultType>,
    ): SearchResults {
        val items = postgres.items.toMutableList()
        val facets = postgres.facets.toMutableList()
        // Number of results before the current segment
        var before = postgres.total
        elasticTypes.forEach { type ->
            val needed = request.size - items.size
            val results = elasticSearchService.paginatedSearch(
                type = type.id,
                token = request.query,
                offset = maxOf(0, request.offset - before),
                size = maxOf(0, needed),
            )
            if (request.offset + items.size >= before) {
                items += results.items.take(maxOf(0, needed))
            }
            before += results.total
            if (results.total > 0) {
                facets += SearchFacet(type, results.total)
            }
        }
        return SearchResults(
            items = items,
            offset = request.offset,
            total = before,
            message = postgres.message,
            facets = facets,
        )
    }

    /**
     * Search on Postgres.
     *
     * @return `null` if the query is too short to be searched
     */
    private fun searchPostgres(request: SearchQueryRequest, types: List<SearchResultType>): SearchResults? {
        if (request.query.trim().length < SearchQueryRequest.MIN_QUERY_LENGTH) {
            return null
        } else if (types.isEmpty()) {
            return SearchResults(items = emptyList(), offset = request.offset, total = 0, message = null)
        }
        val page = searchDocumentService.search(
            query = request.query,
            scope = scope(types),
            offset = request.offset,
            size = request.size,
            perType = request.perType,
        ) ?: return null
        val typesById = types.associateBy { it.id }
        val rebuilding = searchDocumentService.rebuildingTypes
        return SearchResults(
            items = page.items.mapNotNull { hit ->
                typesById[hit.type]?.let { type -> toSearchResult(hit, type) }
            },
            offset = if (request.perType != null) 0 else request.offset,
            total = page.total,
            message = if (types.any { it.id in rebuilding }) MESSAGE_INDEX_BEING_BUILT else null,
            facets = types.mapNotNull { type ->
                page.facets[type.id]?.let { count -> SearchFacet(type, count) }
            },
        )
    }

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
    )

    override fun indexInit() {
        // The Postgres storage is created by the database migrations
        elasticSearchService.indexInit()
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    override fun indexReset(reindex: Boolean, logErrors: Boolean): Ack {
        val postgres = indexers.values.all { indexer ->
            try {
                searchDocumentService.clear(indexer.searchResultType.id)
                if (reindex) {
                    searchDocumentService.rebuild(indexer)
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
        val elastic = elasticSearchService.indexReset(reindex = reindex, logErrors = logErrors)
        return Ack(postgres && elastic.success)
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    override fun reindex(resultType: String) {
        val indexer = indexers[resultType]
        if (indexer != null) {
            searchDocumentService.rebuild(indexer)
        } else {
            elasticSearchService.reindex(resultType)
        }
    }

}
