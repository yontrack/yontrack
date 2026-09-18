package net.nemerosa.ontrack.extension.environments.ui

import net.nemerosa.ontrack.extension.environments.BuildSlotJourneyState
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumBuildSlotJourneyState : AbstractGQLEnum<BuildSlotJourneyState>(
    type = BuildSlotJourneyState::class,
    values = BuildSlotJourneyState.values(),
    description = "Where a build stands in a slot",
)
