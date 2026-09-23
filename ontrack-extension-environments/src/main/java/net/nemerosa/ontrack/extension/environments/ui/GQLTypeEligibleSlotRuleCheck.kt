package net.nemerosa.ontrack.extension.environments.ui

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.environments.EligibleSlotRuleCheck
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.field
import net.nemerosa.ontrack.graphql.support.stringField
import org.springframework.stereotype.Component

@Component
class GQLTypeEligibleSlotRuleCheck : GQLType {
    override fun getTypeName(): String = EligibleSlotRuleCheck::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Admission rule of a slot which prevents a build from being deployed now")
            .field(EligibleSlotRuleCheck::rule, description = "The admission rule")
            .stringField(EligibleSlotRuleCheck::reason, "Why the rule prevents the deployment")
            .build()

}
