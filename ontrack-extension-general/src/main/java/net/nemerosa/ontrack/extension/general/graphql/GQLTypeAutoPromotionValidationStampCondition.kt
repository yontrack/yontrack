package net.nemerosa.ontrack.extension.general.graphql

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.general.AutoPromotionValidationStampCondition
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.*
import org.springframework.stereotype.Component

@Component
class GQLTypeAutoPromotionValidationStampCondition : GQLType {

    override fun getTypeName(): String = AutoPromotionValidationStampCondition::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(getTypeDescription(AutoPromotionValidationStampCondition::class))
            .field(AutoPromotionValidationStampCondition::validationStamp)
            .field(AutoPromotionValidationStampCondition::lastRun)
            .booleanField(AutoPromotionValidationStampCondition::passed)
            .build()
}
