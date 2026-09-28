package net.nemerosa.ontrack.extension.scorecard.graphql

import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumReadingUnknownReason : AbstractGQLEnum<ReadingUnknownReason>(
    ReadingUnknownReason::class,
    ReadingUnknownReason.entries.toTypedArray(),
    "Why a reading is unknown: NO_MARKER (no promotion level on the branches in scope), NO_SAMPLES (nothing reached the marker in the window), NO_FAILURE (time to restore with no failure in the window), NO_TEST_STAMP (no test stamp on the branches in scope)."
)
