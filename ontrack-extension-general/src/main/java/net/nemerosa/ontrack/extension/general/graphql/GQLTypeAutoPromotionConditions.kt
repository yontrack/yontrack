package net.nemerosa.ontrack.extension.general.graphql

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.general.AutoPromotionConditions
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.*
import org.springframework.stereotype.Component

@Component
class GQLTypeAutoPromotionConditions : GQLType {

    override fun getTypeName(): String = AutoPromotionConditions::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(getTypeDescription(AutoPromotionConditions::class))
            .stringField(AutoPromotionConditions::include)
            .stringField(AutoPromotionConditions::exclude)
            .booleanField(AutoPromotionConditions::autoRevoke)
            .listField(AutoPromotionConditions::validationStamps)
            .listField(AutoPromotionConditions::promotionLevels)
            .build()
}
