package net.nemerosa.ontrack.extension.scorecard.service

import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.model.structure.Project
import java.util.concurrent.CompletableFuture

interface ScorecardService {

    /**
     * Scorecard of a project: the latest snapshot of its readings, in every set it is in.
     * A set is listed even when none of its readings has been computed yet.
     */
    fun getScorecard(project: Project): Scorecard

    /**
     * Daily snapshots of a reading over the last [days] days, oldest first.
     */
    fun getHistory(reading: Reading, days: Int): List<Reading>

    /**
     * Target of a reading: the one its estate sets for it, `null` with no estate or no target.
     */
    fun getTarget(reading: Reading): Double?

    /**
     * Queues the recompute of the readings of a project, in every set it is in, overwriting
     * the snapshots of the day. Needs the `ProjectConfig` function on the project.
     *
     * @return The recompute being run, or `null` if one was already running for the project
     */
    fun recompute(project: Project): CompletableFuture<*>?
}
