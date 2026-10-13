package net.nemerosa.ontrack.git

import net.nemerosa.ontrack.git.support.GitConnectionConfig

interface GitRepositoryClientFactory {

    /**
     * Gets the Git client for a given remote repository and the associated credentials.
     *
     * Resetting the clones is not done here, on one replica, but by bumping the reset generation of
     * their repositories, for every replica (#2085): see [GitCloneGenerations].
     */
    fun getClient(repository: GitRepository, gitConnectionConfig: GitConnectionConfig): GitRepositoryClient

}
