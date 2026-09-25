package net.nemerosa.ontrack.graphql.schema

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
                    .description("Number of results for this type")
                    .type(GraphQLNonNull(GraphQLInt))
                    .build()
            )
            .build()
}
