package net.nemerosa.ontrack.extension.scorecard.engine

import net.nemerosa.ontrack.extension.chart.support.Interval

/**
 * Computes one reading of the catalogue, for one subject over one window.
 *
 * Every Spring bean implementing it is computed by the [ReadingEngine], for every set.
 * The engine records the scope and the marker in the details; the computer only says what
 * the value is, or why it is unknown.
 *
 * A computer is expected to be split between the collection of its samples (through
 * the sample functions of the `samples` package, shared with the charts) and their
 * aggregation, a pure function which is unit-tested on plain sample lists.
 */
interface ReadingComputer {

    /**
     * Key of the reading, like `delivery.leadTime`
     */
    val key: String

    /**
     * Computes the reading. Any exception fails the computation of the whole project for the set.
     */
    fun compute(subject: ReadingSubject, window: Interval): ReadingOutcome
}
