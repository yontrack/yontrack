package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import net.nemerosa.ontrack.graphql.support.stringListArgument
import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.structure.SearchQueryRequest
import net.nemerosa.ontrack.model.structure.SearchService
import org.springframework.stereotype.Component

/**
 * Search across the types of results.
 *
 * The `search(token, type, offset, size) { pageInfo pageItems }` form of the query is deprecated
 * and kept until 7.0 as a wrapper of the new one: `token` stands for `query`, `type` for `types`,
 * and `pageInfo` / `pageItems` are computed from the results.
 */
@Component
class GQLRootQuerySearch(
    private val searchResults: GQLTypeSearchResults,
    private val searchService: SearchService,
) : GQLRootQuery {

    companion object {
        private const val ARG_QUERY = "query"
        private const val ARG_TYPES = "types"
        private const val ARG_OFFSET = "offset"
        private const val ARG_SIZE = "size"
        private const val ARG_PER_TYPE = "perType"
        private const val ARG_TOKEN = "token"
        private const val ARG_TYPE = "type"
    }

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("search")
            .description("Performs a search across the types of results, ranked together. Only what the user can see is returned and counted.")
            .argument(
                GraphQLArgument.newArgument()
                    .name(ARG_QUERY)
                    .description("Text to look for, at least 2 characters long. Required, unless the deprecated `token` is used.")
                    .type(GraphQLString)
            )
            .argument(
                stringListArgument(ARG_TYPES, "IDs of the types of results to look into. All of them by default.", nullable = true)
            )
            .argument(
                GraphQLArgument.newArgument()
                    .name(ARG_OFFSET)
                    .description("Index of the first result to return")
                    .type(GraphQLInt)
                    .defaultValueProgrammatic(0)
            )
            .argument(
                GraphQLArgument.newArgument()
                    .name(ARG_SIZE)
                    .description("Maximum number of results to return")
                    .type(GraphQLInt)
                    .defaultValueProgrammatic(SearchQueryRequest.DEFAULT_SIZE)
            )
            .argument(
                GraphQLArgument.newArgument()
                    .name(ARG_PER_TYPE)
                    .description("When set, returns the best N results of each type in one request, instead of a page: offset and size are then ignored.")
                    .type(GraphQLInt)
            )
            .argument(
                GraphQLArgument.newArgument()
                    .name(ARG_TOKEN)
                    .description("Query string")
                    .type(GraphQLString)
                    .deprecate("Use query. Will be removed in 7.0.")
            )
            .argument(
                GraphQLArgument.newArgument()
                    .name(ARG_TYPE)
                    .description("Result type")
                    .type(GraphQLString)
                    .deprecate("Use types. Will be removed in 7.0.")
            )
            .type(GraphQLNonNull(searchResults.typeRef))
            .dataFetcher { env ->
                val query = env.getArgument<String>(ARG_QUERY)
                    ?: env.getArgument<String>(ARG_TOKEN)
                    ?: throw SearchQueryRequiredException()
                val types = env.getArgument<List<String>>(ARG_TYPES)
                    ?: env.getArgument<String>(ARG_TYPE)?.takeIf { it.isNotBlank() }?.let { listOf(it) }
                val offset = env.getArgument<Int>(ARG_OFFSET) ?: 0
                val size = env.getArgument<Int>(ARG_SIZE) ?: SearchQueryRequest.DEFAULT_SIZE
                val perType = env.getArgument<Int>(ARG_PER_TYPE)
                GQLSearchResults(
                    results = searchService.search(
                        SearchQueryRequest(
                            query = query,
                            types = types,
                            offset = offset,
                            size = size,
                            perType = perType,
                        )
                    ),
                    offset = offset,
                    size = size,
                )
            }
            .build()

}

/**
 * Neither `query` nor the deprecated `token` was given.
 */
class SearchQueryRequiredException : InputException("A query is required")
