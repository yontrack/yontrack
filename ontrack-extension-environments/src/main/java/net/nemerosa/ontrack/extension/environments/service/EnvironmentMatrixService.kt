package net.nemerosa.ontrack.extension.environments.service

import net.nemerosa.ontrack.extension.environments.EnvironmentMatrix
import net.nemerosa.ontrack.extension.environments.EnvironmentMatrixFilter

/**
 * The project x environment matrix, as one query.
 *
 * The screen it serves is the Environments home, and the reason it is a service of its own rather
 * than "the environments list, with more fields" is the cost. The list resolves one environment at a
 * time and then one slot at a time inside it; the matrix is the *cross product* of a page of
 * projects and every environment, so the same shape would cost a query per cell. Everything here is
 * therefore expressed over the whole page at once, and the single-slot readings of [SlotStatusService]
 * are reached through their batch form.
 */
interface EnvironmentMatrixService {

    /**
     * One page of the matrix.
     *
     * @param filter What narrows it down. Applied in SQL, before paging: a page of projects of which
     *   the caller then hides half is not a page.
     * @param offset Which project to start at, in project-name order.
     * @param size How many projects to return.
     */
    fun matrix(
        filter: EnvironmentMatrixFilter = EnvironmentMatrixFilter(),
        offset: Int = 0,
        size: Int = DEFAULT_SIZE,
    ): EnvironmentMatrix

    companion object {
        /**
         * How many projects a page holds when nobody says otherwise.
         *
         * Twenty rows is about a screenful of matrix, and the design target is a hundred projects:
         * five pages, each one query deep.
         */
        const val DEFAULT_SIZE = 20
    }

}
