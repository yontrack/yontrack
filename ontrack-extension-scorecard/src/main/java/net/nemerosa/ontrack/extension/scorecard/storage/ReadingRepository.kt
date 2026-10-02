package net.nemerosa.ontrack.extension.scorecard.storage

import net.nemerosa.ontrack.extension.scorecard.model.Reading
import java.time.LocalDate

/**
 * Storage of the daily snapshots of the readings.
 */
interface ReadingRepository {

    /**
     * Stores the readings, each one overwriting the one of the same set, project, reading and day.
     */
    fun save(readings: List<Reading>)

    /**
     * Latest snapshot of every reading of a project, all sets together.
     */
    fun findLatestByProject(projectId: Int): List<Reading>

    /**
     * Latest snapshot of every reading of every project in the set of an estate.
     */
    fun findLatestByEstate(estateId: Int): List<Reading>

    /**
     * Daily snapshots of one reading of a project in one set, from [since] included, oldest first.
     *
     * @param estateId Estate of the set, `null` for the no-estate set
     */
    fun findHistory(estateId: Int?, projectId: Int, key: String, since: LocalDate): List<Reading>

    /**
     * Reads every snapshot, page by page, in no meaningful order.
     *
     * @param estates `false` to read only the snapshots of the no-estate set
     * @param pageSize Maximum number of snapshots in a page
     * @param code Called for each page, never empty
     */
    fun forEachPage(estates: Boolean, pageSize: Int, code: (List<Reading>) -> Unit)

    /**
     * Deletes the snapshots of a day before [day] (excluded).
     *
     * @return Number of deleted snapshots
     */
    fun deleteBefore(day: LocalDate): Int
}
