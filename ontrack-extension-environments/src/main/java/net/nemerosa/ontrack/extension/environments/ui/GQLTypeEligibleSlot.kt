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
            .description("Association of a slot with the eligibility and deployability of a build")
            .booleanField(
                EligibleSlot::eligible,
                "Whether the build could go to this slot - a pipeline can be started for it, as a candidate.",
            )
            .listField(
                EligibleSlot::nonEligibleRules,
                "Admission rules of the slot which refuse this build. Empty when the build is eligible.",
            )
            .booleanField(
                EligibleSlot::deployable,
                "Whether the build is eligible and can be deployed now: it passes every admission rule which can be decided on the build alone. A pipeline started for an eligible but not deployable build waits as a candidate.",
            )
            .listField(
                EligibleSlot::nonDeployableRules,
                "Admission rules of the slot which prevent this eligible build from being deployed now, with their reasons. Empty when the build is deployable, or not eligible at all.",
            )
            .listField(
                EligibleSlot::pipelineOnlyRules,
                "Admission rules of the slot which can only be decided once a pipeline exists for the build (like a manual approval). They do not count against deployable.",
            )
            .field(EligibleSlot::slot)
            .build()

}
