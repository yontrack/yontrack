package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars.GraphQLFloat
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.graphql.support.jsonField
import net.nemerosa.ontrack.model.structure.SearchResult
import org.springframework.stereotype.Component

/**
 * Type for [SearchResult].
 */
@Component
class GQLTypeSearchResult(
    private val searchResultType: GQLTypeSearchResultType
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
                it.name("uri")
                    .deprecate("Removed in V6. No replacement. See #1921")
                    .description("API access point")
                    .type(GraphQLString)
            }
            .field {
                it.name("page")
                    .deprecate("Removed in V6. No replacement. See #1921")
                    .description("Web access point")
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
            .build()
    }

    override fun getTypeName(): String = SearchResult::class.java.simpleName
}
