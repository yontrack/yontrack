package net.nemerosa.ontrack.extension.bitbucket.cloud.client

import net.nemerosa.ontrack.extension.bitbucket.cloud.model.BitbucketCloudRepository
import java.time.LocalDateTime

/**
 * Interface which defines how we talk to Bitbucket Cloud.
 */
interface BitbucketCloudClient {

    /**
     * Checks that the credentials are accepted by Bitbucket Cloud. Throws an exception if not.
     *
     * - API token: `GET /2.0/user`
     * - access token: `GET /2.0/hook_events` - `/2.0/user` is refused to access tokens, which have no user,
     *   while `/2.0/hook_events` needs no scope but rejects an invalid token with a 401.
     */
    fun validate()

    /**
     * Gets all repositories of a workspace.
     */
    fun getRepositories(workspace: String): List<BitbucketCloudRepository>

    /**
     * Given a [repository], returns its last modification date (if any).
     */
    fun getRepositoryLastModified(repository: BitbucketCloudRepository): LocalDateTime?

    /**
     * Given a [repository], returns its creation date (if any).
     */
    fun getRepositoryCreationDate(repository: BitbucketCloudRepository): LocalDateTime?

    /**
     * Gets the repository information.
     *
     * @param workspace Workspace slug
     * @param repository Repository slug
     */
    fun getRepository(workspace: String, repository: String): BitbucketCloudRepository

}
