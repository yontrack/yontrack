package net.nemerosa.ontrack.extension.scorecard.service

import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingSet
import net.nemerosa.ontrack.model.structure.Project

/**
 * The readings of one project: one [set][ScorecardSet] per set the project is in.
 */
data class Scorecard(
    val project: Project,
    val sets: List<ScorecardSet>,
)

/**
 * The latest snapshot of each reading of a project in one set, in the catalogue order.
 */
data class ScorecardSet(
    val set: ReadingSet,
    val project: Project,
    val readings: List<Reading>,
)
