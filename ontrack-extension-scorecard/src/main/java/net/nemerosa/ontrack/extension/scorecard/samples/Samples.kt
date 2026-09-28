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
