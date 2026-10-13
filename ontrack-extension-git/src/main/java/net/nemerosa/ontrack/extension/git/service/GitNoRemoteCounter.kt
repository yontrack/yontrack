package net.nemerosa.ontrack.extension.git.service

import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.support.StorageService
import org.springframework.stereotype.Component

/**
 * Number of the synchronisations of a project which failed in a row because its remote did not
 * exist, counted for all the replicas: in the database, incremented atomically (#2088).
 */
@Component
class GitNoRemoteCounter(
    private val storageService: StorageService,
) {

    fun getNoRemoteCount(project: Project): Int = storageService.getCount(STORE, key(project))

    fun resetNoRemoteCount(project: Project) {
        storageService.delete(STORE, key(project))
    }

    /**
     * Counts one more failure.
     *
     * @return Number of failures in a row, this one included
     */
    fun incNoRemoteCount(project: Project): Int = storageService.increment(STORE, key(project))

    private fun key(project: Project) = project.id.toString()

    companion object {
        /**
         * Store of the counters, one per project, by ID
         */
        val STORE: String = GitNoRemoteCounter::class.java.name
    }
}
