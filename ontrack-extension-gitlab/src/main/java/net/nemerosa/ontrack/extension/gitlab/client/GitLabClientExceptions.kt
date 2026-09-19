package net.nemerosa.ontrack.extension.gitlab.client

import net.nemerosa.ontrack.common.BaseException

/**
 * Any error while talking to GitLab.
 */
open class GitLabClientException(message: String) : BaseException("%s", message)

/**
 * GitLab kept answering 429 after every retry.
 */
class GitLabRateLimitException(attempts: Int) : GitLabClientException(
    "GitLab rate limit still in force after $attempts attempts."
)

/**
 * GitLab answered with no body where one was expected.
 */
class GitLabNoResponseException(uri: String) : GitLabClientException(
    "No response from GitLab for $uri."
)

/**
 * GitLab refused to create a branch, or created one whose head it did not return.
 */
class GitLabCannotCreateBranchException(project: String, branch: String, source: String) : GitLabClientException(
    "Cannot create the branch $branch from $source in the GitLab project $project."
)

/**
 * GitLab refused to create a merge request, or created one it did not return.
 */
class GitLabCannotCreateMergeRequestException(project: String, from: String, to: String) : GitLabClientException(
    "Cannot create a merge request from $from to $to in the GitLab project $project."
)

/**
 * GitLab accepted the merge call but returned nothing to read the outcome from.
 */
class GitLabCannotMergeMergeRequestException(project: String, iid: Int) : GitLabClientException(
    "No answer from GitLab when merging the merge request !$iid of the project $project."
)

/**
 * GitLab answered with a redirect, which this client does not follow.
 *
 * Following one would replay the `PRIVATE-TOKEN` header onto whatever host the `Location` names - the same
 * exposure as a cross-host `Link` header. No GitLab API v4 endpoint Yontrack calls redirects, so this points
 * at a configuration whose URL is not the one the instance answers on, typically an `http://` URL on an
 * instance which forces `https://`.
 */
class GitLabRedirectException(uri: String, location: String?) : GitLabClientException(
    "GitLab redirected $uri to ${location ?: "an unnamed location"}. Yontrack does not follow redirects, " +
            "because the redirected request would carry the access token. Check the URL of the configuration."
)
