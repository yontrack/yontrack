package net.nemerosa.ontrack.graphql.schema

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.graphql.support.booleanField
import net.nemerosa.ontrack.graphql.support.intIdField
import net.nemerosa.ontrack.graphql.support.stringField
import net.nemerosa.ontrack.model.structure.PredefinedPromotionLevel
import org.springframework.stereotype.Component

@Component
class GQLTypePredefinedPromotionLevel: GQLType {

    override fun getTypeName(): String  = PredefinedPromotionLevel::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType = GraphQLObjectType.newObject()
        .name(typeName)
        .description("Predefined promotion level")
        .intIdField(PredefinedPromotionLevel::id)
        .stringField(PredefinedPromotionLevel::name)
        .stringField(PredefinedPromotionLevel::description)
        .booleanField(PredefinedPromotionLevel::isImage)
        .build()
}