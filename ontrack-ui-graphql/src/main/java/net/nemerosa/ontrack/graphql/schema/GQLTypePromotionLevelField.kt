package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.model.structure.PromotionLevelField
import org.springframework.stereotype.Component

@Component
class GQLTypePromotionLevelField : GQLType {

    override fun getTypeName(): String = PROMOTION_LEVEL_FIELD

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(PROMOTION_LEVEL_FIELD)
            .description("Configurable field definition on a promotion level")
            .field {
                it.name("id").description("Internal ID").type(GraphQLNonNull(Scalars.GraphQLInt))
            }
            .field {
                it.name("name").description("Technical name (used as key)").type(GraphQLNonNull(Scalars.GraphQLString))
            }
            .field {
                it.name("displayName").description("Human-readable label").type(GraphQLNonNull(Scalars.GraphQLString))
            }
            .field {
                it.name("description").description("Optional description").type(Scalars.GraphQLString)
            }
            .field {
                it.name("type")
                    .description("Field type")
                    .type(GraphQLNonNull(GraphQLTypeReference("PromotionLevelFieldType")))
            }
            .field {
                it.name("required").description("Whether this field is required").type(GraphQLNonNull(Scalars.GraphQLBoolean))
            }
            .field {
                it.name("options")
                    .description("Allowed values for CHOICE fields")
                    .type(listType(Scalars.GraphQLString))
            }
            .field {
                it.name("position").description("Display order").type(GraphQLNonNull(Scalars.GraphQLInt))
            }
            .build()

    companion object {
        const val PROMOTION_LEVEL_FIELD = "PromotionLevelField"
    }
}
