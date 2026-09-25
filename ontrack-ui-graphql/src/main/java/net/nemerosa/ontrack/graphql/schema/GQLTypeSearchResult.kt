package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars.GraphQLFloat
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.graphql.support.jsonField
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.model.structure.SearchResult
import org.springframework.stereotype.Component

/**
 * Type for [SearchResult].
 */
@Component
class GQLTypeSearchResult(
    private val searchResultType: GQLTypeSearchResultType,
    private val searchHighlightPart: GQLTypeSearchHighlightPart,
) : GQLType {

    override fun createType(cache: GQLTypeCache): GraphQLObjectType {
        return GraphQLObjectType.newObject()
            .name(typeName)
            .description("Search result")
            .field {
                it.name("title")
                    .description("Short title")
                    .type(GraphQLString)
            }
            .field {
                it.name("description")
                    .description("Description linked to the item being found")
                    .type(GraphQLString)
            }
            .field {
                it.name("accuracy")
                    .description("Score for the search")
                    .type(GraphQLFloat)
            }
            .field {
                it.name("type")
                    .description("Type of result")
                    .type(searchResultType.typeRef)
            }
            .jsonField(SearchResult::data)
            .field {
                it.name("highlight")
                    .description(
                        "Excerpt of the free text of the result - a description, a commit message - where it matches the query, " +
                                "as a sequence of parts, the matching ones flagged. " +
                                "Computed only when selected, and only for the results returned. " +
                                "Null when the result has no free text, or when its free text does not match any word of the query."
                    )
                    .type(listType(searchHighlightPart.typeRef, nullable = true))
            }
            .build()
    }

    override fun getTypeName(): String = SearchResult::class.java.simpleName
}
