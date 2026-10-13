package net.nemerosa.ontrack.extension.git.service

import net.nemerosa.ontrack.git.GitCloneGenerations
import net.nemerosa.ontrack.git.GitRepository

/**
 * Reset generations of the Git repositories, in the database, the same on every replica (#2085).
 *
 * A reset never deletes a clone: it bumps the generation, and each replica whose clone is older
 * deletes it and clones it again on its next use. No replica has to be told.
 */
interface GitCloneGenerationService : GitCloneGenerations {

    /**
     * Resets the clones of a repository, on every replica.
     */
    fun reset(repository: GitRepository)

    /**
     * Resets the clones of all the repositories, on every replica.
     */
    fun resetAll()

}
