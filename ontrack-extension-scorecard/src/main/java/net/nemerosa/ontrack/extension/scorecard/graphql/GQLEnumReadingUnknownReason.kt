package net.nemerosa.ontrack.extension.scorecard.graphql

import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumReadingUnknownReason : AbstractGQLEnum<ReadingUnknownReason>(
    ReadingUnknownReason::class,
    ReadingUnknownReason.entries.toTypedArray(),
    "Why a reading is unknown: NO_MARKER (no promotion level on the branches in scope, or no slot of the project in the marker environment), NO_SAMPLES (nothing to measure in the window: nothing reached the marker, or no CRITICAL or HIGH finding was resolved), NO_FAILURE (time to restore with no failure in the window), NO_TEST_STAMP (no test stamp on the branches in scope), NOT_LICENSED (environment marker without the environments licence), NO_TARGET (overdue findings with no remediation target: no estate, or an estate with neither a CRITICAL nor a HIGH target)."
)
