package net.nemerosa.ontrack.extension.gitlab.client

import net.nemerosa.ontrack.extension.gitlab.model.GitLabIssue
import net.nemerosa.ontrack.extension.gitlab.model.GitLabMergeRequest
import net.nemerosa.ontrack.extension.gitlab.model.GitLabProject

/**
 * Client used to connect to a GitLab instance from Yontrack.
 *
 * Everywhere a project is named, it is by its **full path**, subgroups included -
 * `group/subgroup/project` - which the client URL-encodes at call time.
 */
interface GitLabClient {

    /**
     * Checks the configuration can be used: fails when the instance cannot be reached or the token is
     * refused.
     */
    fun validate()

    /**
     * Projects this client is a member of.
     */
    fun getProjects(): List<GitLabProject>

    /**
     * Gets an issue of a project.
     *
     * @param project Full path of the project, like `group/subgroup/project`
     * @param iid Number of the issue **inside the project** - the one a `#123` reference names
     * @return The issue, or `null` when there is no such issue
     */
    fun getIssue(project: String, iid: Int): GitLabIssue?

    /**
     * Gets a merge request of a project.
     *
     * @param project Full path of the project, like `group/subgroup/project`
     * @param iid Number of the merge request **inside the project**
     * @return The merge request, or `null` when there is no such merge request
     */
    fun getMergeRequest(project: String, iid: Int): GitLabMergeRequest?

}
