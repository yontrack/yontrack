package net.nemerosa.ontrack.extension.scorecard.graphql

import net.nemerosa.ontrack.extension.scorecard.model.ReadingDirection
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumReadingDirection : AbstractGQLEnum<ReadingDirection>(
    ReadingDirection::class,
    ReadingDirection.entries.toTypedArray(),
    "Which way a reading is better: LOWER_IS_BETTER (a target is met by a value lower than or equal to it) or HIGHER_IS_BETTER (a target is met by a value higher than or equal to it)."
)
