package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars.GraphQLBoolean
import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.graphql.support.pagination.GQLTypePageInfo
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.structure.SearchResults
import org.springframework.stereotype.Component

/**
 * Results of the `search` query, with the page they were asked for.
 */
class GQLSearchResults(
    val results: SearchResults,
    val offset: Int,
    val size: Int,
) {
    /**
     * Pagination of the deprecated `search(token, type)` form
     */
    val paginatedList: PaginatedList<*>
        get() = PaginatedList.create(
            items = results.items,
            offset = offset,
            pageSize = size,
            total = results.total,
        )
}

/**
 * Type for the results of the `search` query.
 */
@Component
class GQLTypeSearchResults(
    private val searchResult: GQLTypeSearchResult,
    private val searchFacet: GQLTypeSearchFacet,
    private val pageInfo: GQLTypePageInfo,
) : GQLType {

    override fun getTypeName(): String = "SearchResults"

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Results of a search")
            .field(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("total")
                    .description("Total number of results, for what the user can see: the sum of the counts of the facets")
                    .type(GraphQLNonNull(GraphQLInt))
                    .dataFetcher { env -> env.getSource<GQLSearchResults>()!!.results.total }
                    .build()
            )
            .field(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("capped")
                    .description("True when the count of one of the types is capped: there are more results than the total")
                    .type(GraphQLNonNull(GraphQLBoolean))
                    .dataFetcher { env -> env.getSource<GQLSearchResults>()!!.results.capped }
                    .build()
            )
            .field(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("facets")
                    .description("Number of results per type, for the types having some")
                    .type(listType(searchFacet.typeRef))
                    .dataFetcher { env -> env.getSource<GQLSearchResults>()!!.results.facets }
                    .build()
            )
            .field(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("items")
                    .description("Results, best first")
                    .type(listType(searchResult.typeRef))
                    .dataFetcher { env -> env.getSource<GQLSearchResults>()!!.results.items }
                    .build()
            )
            .field(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("message")
                    .description("Message about the search, for example when the search index is being built")
                    .type(GraphQLString)
                    .dataFetcher { env -> env.getSource<GQLSearchResults>()!!.results.message }
                    .build()
            )
            .field(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("pageInfo")
                    .description("Information about the current page")
                    .deprecate("Use total, and the offset and size arguments. Will be removed in 7.0.")
                    .type(pageInfo.typeRef)
                    .dataFetcher { env -> env.getSource<GQLSearchResults>()!!.paginatedList.pageInfo }
                    .build()
            )
            .field(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("pageItems")
                    .description("Items in the current page")
                    .deprecate("Use items. Will be removed in 7.0.")
                    .type(listType(searchResult.typeRef))
                    .dataFetcher { env -> env.getSource<GQLSearchResults>()!!.results.items }
                    .build()
            )
            .build()
}
