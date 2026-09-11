package net.nemerosa.ontrack.extension.environments.ui

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.environments.EligibleSlot
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.booleanField
import net.nemerosa.ontrack.graphql.support.field
import net.nemerosa.ontrack.graphql.support.listField
import org.springframework.stereotype.Component

@Component
class GQLTypeEligibleSlot : GQLType {
    override fun getTypeName(): String = EligibleSlot::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Association of a slot with its eligibility")
            .booleanField(EligibleSlot::eligible)
            .listField(
                EligibleSlot::nonEligibleRules,
                "Admission rules of the slot which refuse this build. Empty when the build is eligible.",
            )
            .field(EligibleSlot::slot)
            .build()

}
