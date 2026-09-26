package net.nemerosa.ontrack.extension.general.graphql

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.general.AutoPromotionBuildConditions
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.*
import org.springframework.stereotype.Component

@Component
class GQLTypeAutoPromotionBuildConditions : GQLType {

    override fun getTypeName(): String = AutoPromotionBuildConditions::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(getTypeDescription(AutoPromotionBuildConditions::class))
            .stringField(AutoPromotionBuildConditions::include)
            .stringField(AutoPromotionBuildConditions::exclude)
            .booleanField(AutoPromotionBuildConditions::autoRevoke)
            .listField(AutoPromotionBuildConditions::validationStamps)
            .listField(AutoPromotionBuildConditions::promotionLevels)
            .build()
}
