package net.nemerosa.ontrack.extension.scorecard.engine

import net.nemerosa.ontrack.extension.scorecard.model.ReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.model.structure.Project

/**
 * What a project is read on, for one set: its scope, and the marker its delivery readings
 * measure up to.
 *
 * @property markerKind Kind of marker the set reads up to, known even when no marker could be resolved
 * @property marker Marker resolved for this project, `null` when there is none
 * @property noMarkerReason Why there is no marker, when there is none: `NO_MARKER`, or `NOT_LICENSED`
 * for an environment marker without the environments licence
 */
data class ReadingSubject(
    val set: ReadingSet,
    val project: Project,
    val scope: ReadingScope,
    val markerKind: MarkerKind,
    val marker: Marker?,
    val noMarkerReason: ReadingUnknownReason = ReadingUnknownReason.NO_MARKER,
)
