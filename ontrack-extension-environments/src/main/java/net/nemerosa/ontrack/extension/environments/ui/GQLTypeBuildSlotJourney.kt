package net.nemerosa.ontrack.extension.environments.ui

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.environments.BuildSlotJourney
import net.nemerosa.ontrack.extension.environments.BuildSlotJourneyState
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.enumField
import net.nemerosa.ontrack.graphql.support.field
import net.nemerosa.ontrack.graphql.support.listField
import org.springframework.stereotype.Component

@Component
class GQLTypeBuildSlotJourney : GQLType {

    override fun getTypeName(): String = BuildSlotJourney::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Where one build stands in one slot of its project")
            .field(BuildSlotJourney::slot)
            .enumField(BuildSlotJourney::state, "Where the build stands in this slot")
            .field(BuildSlotJourney::pipeline)
            .listField(
                BuildSlotJourney::nonEligibleRules,
                "Admission rules of the slot refusing this build. Empty unless the state is NOT_ELIGIBLE.",
            )
            .build()
}
