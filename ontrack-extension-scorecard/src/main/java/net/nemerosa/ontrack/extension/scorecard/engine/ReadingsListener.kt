package net.nemerosa.ontrack.extension.scorecard.engine

import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingSet
import net.nemerosa.ontrack.model.structure.Project

/**
 * Told about the readings of a project, for one set, each time they are computed and stored.
 *
 * Not told when the computation fails: there are no readings then.
 */
interface ReadingsListener {

    fun onReadings(set: ReadingSet, project: Project, readings: List<Reading>)
}
