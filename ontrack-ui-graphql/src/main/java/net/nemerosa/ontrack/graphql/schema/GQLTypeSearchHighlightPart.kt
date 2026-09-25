package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars.GraphQLBoolean
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.model.structure.SearchHighlightPart
import org.springframework.stereotype.Component

/**
 * Type for [SearchHighlightPart].
 */
@Component
class GQLTypeSearchHighlightPart : GQLType {

    override fun getTypeName(): String = SearchHighlightPart::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Part of the highlighted free text of a search result. The parts, joined, are an excerpt of the text: plain text, whatever it contains, never markup.")
            .field(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("text")
                    .description("Part of the text")
                    .type(GraphQLNonNull(GraphQLString))
                    .build()
            )
            .field(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("match")
                    .description("True if this part matches the query")
                    .type(GraphQLNonNull(GraphQLBoolean))
                    .build()
            )
            .build()
}
