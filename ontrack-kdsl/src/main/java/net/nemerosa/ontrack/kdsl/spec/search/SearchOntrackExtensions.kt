package net.nemerosa.ontrack.kdsl.spec.search

import net.nemerosa.ontrack.kdsl.connector.graphql.paginate
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.SearchQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.SearchResultsQuery
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.connector.support.PaginatedList
import net.nemerosa.ontrack.kdsl.connector.support.emptyPaginatedList
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import com.apollographql.apollo.api.Optional

/**
 * Search on one type of results.
 *
 * Uses the deprecated `search(token, type)` form of the query, kept until 7.0.
 */
fun Ontrack.search(type: String, token: String): PaginatedList<SearchResult> =
    graphqlConnector.query(
        SearchQuery(type, token, 40)
    )?.paginate(
        pageInfo = { it.search.pageInfo?.pageInfoContent },
        pageItems = { it.search.pageItems }
    )?.map {
        SearchResult(
            title = it.title ?: "",
            description = it.description ?: "",
            accuracy = it.accuracy ?: 0.0,
            type = SearchResultType(
                feature = it.type?.feature?.id ?: "",
                id = it.type?.id ?: "",
                name = it.type?.name ?: "",
                description = it.type?.description ?: "",
            ),
        )
    } ?: emptyPaginatedList()

/**
 * Search across types of results, ranked together.
 *
 * @param query Text to look for, at least 2 characters long
 * @param types IDs of the types to look into, all of them by default
 * @param offset Index of the first result to return
 * @param size Maximum number of results to return
 * @param perType When set, returns the best [perType] results of each type, ignoring [offset] and [size]
 */
fun Ontrack.search(
    query: String,
    types: List<String>? = null,
    offset: Int = 0,
    size: Int = 20,
    perType: Int? = null,
): SearchResults =
    graphqlConnector.query(
        SearchResultsQuery(
            query = query,
            types = Optional.presentIfNotNull(types),
            offset = offset,
            size = size,
            perType = Optional.presentIfNotNull(perType),
        )
    )?.search?.let { search ->
        SearchResults(
            total = search.total,
            facets = search.facets.map { facet ->
                SearchFacet(
                    type = SearchResultType(
                        feature = facet.type.feature?.id ?: "",
                        id = facet.type.id ?: "",
                        name = facet.type.name ?: "",
                        description = facet.type.description ?: "",
                    ),
                    count = facet.count,
                )
            },
            items = search.items.map { item ->
                SearchResultItem(
                    title = item.title ?: "",
                    description = item.description ?: "",
                    accuracy = item.accuracy ?: 0.0,
                    type = SearchResultType(
                        feature = item.type?.feature?.id ?: "",
                        id = item.type?.id ?: "",
                        name = item.type?.name ?: "",
                        description = item.type?.description ?: "",
                    ),
                    data = item.data,
                )
            },
            message = search.message,
        )
    } ?: SearchResults(total = 0, facets = emptyList(), items = emptyList(), message = null)
