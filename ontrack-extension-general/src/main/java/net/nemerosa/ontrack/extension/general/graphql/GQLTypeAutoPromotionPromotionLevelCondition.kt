package net.nemerosa.ontrack.extension.general.graphql

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.general.AutoPromotionPromotionLevelCondition
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.*
import org.springframework.stereotype.Component

@Component
class GQLTypeAutoPromotionPromotionLevelCondition : GQLType {

    override fun getTypeName(): String = AutoPromotionPromotionLevelCondition::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(getTypeDescription(AutoPromotionPromotionLevelCondition::class))
            .field(AutoPromotionPromotionLevelCondition::promotionLevel)
            .field(AutoPromotionPromotionLevelCondition::promotionRun)
            .build()
}
