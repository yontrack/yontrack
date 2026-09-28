package net.nemerosa.ontrack.extension.scorecard.samples

import java.time.Duration
import java.time.LocalDateTime

/**
 * A duration from a [start] to the moment the marker was reached ([end]).
 *
 * A reading keeps the samples whose [end] is in its window. A chart may bucket them by either end.
 *
 * @property branchId Branch the sample was taken on
 */
data class DurationSample(
    val branchId: Int,
    val start: LocalDateTime,
    val end: LocalDateTime,
) {
    /**
     * Duration in seconds
     */
    val seconds: Double get() = Duration.between(start, end).toSeconds().toDouble()
}

/**
 * One event which reached the marker, at [time].
 *
 * @property branchId Branch the event occurred on
 */
data class EventSample(
    val branchId: Int,
    val time: LocalDateTime,
)

/**
 * A build on a branch read up to a promotion level, and when it reached that level.
 *
 * @property branchId Branch of the build
 * @property buildId ID of the build, breaking the ties between builds created at the same time
 * @property creation Creation of the build
 * @property promotion First promotion run of the build at the level, `null` if the build was not
 * promoted before the end of the interval the sample was taken for
 */
data class BuildSample(
    val branchId: Int,
    val buildId: Int,
    val creation: LocalDateTime,
    val promotion: LocalDateTime?,
) {
    /**
     * Has the build reached the level?
     */
    val promoted: Boolean get() = promotion != null
}

/**
 * An outage of a branch: from the creation of the **first** unpromoted build after a promoted one
 * ([start]) to the next promotion on that branch ([restored]).
 *
 * @property branchId Branch the outage occurred on
 * @property restored Time of the promotion which ended the outage, `null` while it lasts
 */
data class OutageSample(
    val branchId: Int,
    val start: LocalDateTime,
    val restored: LocalDateTime?,
) {
    /**
     * Is the outage still going on?
     */
    val open: Boolean get() = restored == null

    /**
     * Time to restore, `null` while the outage lasts
     */
    val timeToRestore: DurationSample?
        get() = restored?.let { DurationSample(branchId = branchId, start = start, end = it) }
}
