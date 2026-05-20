package net.nemerosa.ontrack.git

import net.nemerosa.ontrack.git.support.GitConnectionConfig

interface GitRepositoryClientFactory {

    /**
     * Gets the Git client for a given remote repository and the associated credentials.
     */
    fun getClient(repository: GitRepository, gitConnectionConfig: GitConnectionConfig): GitRepositoryClient

    /**
     * Resets all repositories, forcing them to be closed again.
     */
    fun reset()

}
