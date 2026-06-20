package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.graphql.support.GQLScalarJSON
import org.springframework.stereotype.Component

@Component
class GQLTypePromotionRunFieldValue : GQLType {

    override fun getTypeName(): String = PROMOTION_RUN_FIELD_VALUE

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(PROMOTION_RUN_FIELD_VALUE)
            .description("A field value stored on a promotion run")
            .field {
                it.name("name").description("Field name (key)").type(GraphQLNonNull(Scalars.GraphQLString))
            }
            .field {
                it.name("value").description("Field value as JSON (null for optional unfilled fields)")
                    .type(GQLScalarJSON.INSTANCE)
            }
            .build()

    companion object {
        const val PROMOTION_RUN_FIELD_VALUE = "PromotionRunFieldValue"
    }
}
