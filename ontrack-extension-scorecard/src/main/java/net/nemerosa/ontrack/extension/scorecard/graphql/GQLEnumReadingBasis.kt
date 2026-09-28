package net.nemerosa.ontrack.extension.scorecard.graphql

import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumReadingBasis : AbstractGQLEnum<ReadingBasis>(
    ReadingBasis::class,
    ReadingBasis.entries.toTypedArray(),
    "What the value of a reading rests on: MEASURED from Yontrack's own data, ESTIMATED (never produced in 6.x) or UNKNOWN, with a reason."
)
