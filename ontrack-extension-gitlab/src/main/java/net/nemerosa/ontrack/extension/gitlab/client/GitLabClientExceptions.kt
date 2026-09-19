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
