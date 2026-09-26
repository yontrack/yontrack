package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars.GraphQLBoolean
import graphql.Scalars.GraphQLInt
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.model.structure.SearchFacet
import org.springframework.stereotype.Component

/**
 * Type for [SearchFacet].
 */
@Component
class GQLTypeSearchFacet(
    private val searchResultType: GQLTypeSearchResultType,
) : GQLType {

    override fun getTypeName(): String = SearchFacet::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Number of search results for one type")
            .field(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("type")
                    .description("Type of result")
                    .type(GraphQLNonNull(searchResultType.typeRef))
                    .build()
            )
            .field(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("count")
                    .description("Number of results for this type, at most the cap of the counts (`ontrack.config.search.count-cap`)")
                    .type(GraphQLNonNull(GraphQLInt))
                    .build()
            )
            .field(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("capped")
                    .description("True when this type has more results than its count, which is then the cap: shown as `1000+`")
                    .type(GraphQLNonNull(GraphQLBoolean))
                    .build()
            )
            .build()
}
