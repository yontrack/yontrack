package net.nemerosa.ontrack.extension.gitlab.client

import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration

/**
 * Creates a client for accessing GitLab.
 */
interface GitLabClientFactory {

    /**
     * Given a GitLab configuration, creates a GitLab client.
     */
    fun create(configuration: GitLabConfiguration): GitLabClient

}
