package net.nemerosa.ontrack.git

/**
 * Reset generations of the repositories, the same on every replica (#2085).
 *
 * Each replica has clones of its own. Resetting a repository bumps its generation, and a replica
 * whose clone was made at an older generation deletes it and clones it again on its next use: no
 * replica has to be told.
 */
fun interface GitCloneGenerations {

    /**
     * Current reset generation of a repository, 0 when it was never reset.
     */
    fun getGeneration(repository: GitRepository): Long

    companion object {
        /**
         * Repositories which are never reset.
         */
        val NONE = GitCloneGenerations { 0L }
    }
}
