package net.nemerosa.ontrack.git.support

import net.nemerosa.ontrack.git.GitCloneGenerations
import net.nemerosa.ontrack.git.GitRepository
import net.nemerosa.ontrack.git.GitRepositoryClient
import net.nemerosa.ontrack.git.GitRepositoryClientFactory
import net.nemerosa.ontrack.git.exceptions.GitRepositoryDirException
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.time.Duration

/**
 * Gives the clients of the clones of this replica, in [root].
 *
 * @param freshness How old the last fetch of a clone can be when it is read
 * @param generations Reset generations of the repositories, the same on every replica
 */
class GitRepositoryClientFactoryImpl(
    private val root: File,
    private val timeout: Duration = Duration.ofSeconds(60),
    private val operationTimeout: Duration = Duration.ofMinutes(10),
    private val retries: UInt = 3u,
    private val interval: Duration = Duration.ofSeconds(30),
    private val freshness: Duration = GitRepositoryClientImpl.DEFAULT_FRESHNESS,
    private val generations: GitCloneGenerations = GitCloneGenerations.NONE,
) : GitRepositoryClientFactory {

    override fun getClient(repository: GitRepository, gitConnectionConfig: GitConnectionConfig): GitRepositoryClient {
        // Directory for this repository
        val repositoryDir = File(root, repository.id)
        // Makes sure the directory is ready
        try {
            FileUtils.forceMkdir(repositoryDir)
        } catch (ex: IOException) {
            throw GitRepositoryDirException(repositoryDir, ex)
        }

        // Creates the client
        return GitRepositoryClientImpl(
            repositoryDir = repositoryDir,
            repository = repository,
            timeout = timeout,
            operationTimeout = operationTimeout,
            retries = retries,
            interval = interval,
            config = gitConnectionConfig,
            freshness = freshness,
            generations = generations,
        )
    }

}
