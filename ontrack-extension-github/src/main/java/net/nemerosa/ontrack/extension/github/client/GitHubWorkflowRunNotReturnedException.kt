package net.nemerosa.ontrack.extension.github.client

import net.nemerosa.ontrack.common.BaseException

/**
 * A workflow has been dispatched without the `id` input, but GitHub did not return its run.
 *
 * Without the `id` input, the run cannot be searched for: the GitHub instance must return
 * the run details, or the `id` input must be sent.
 *
 * @param repository Repository containing the workflow
 * @param workflow Workflow file name
 */
class GitHubWorkflowRunNotReturnedException(
    val repository: String,
    val workflow: String,
) : BaseException(
    "Workflow $repository/$workflow was dispatched without the id input, but GitHub did not return its run. " +
            "This GitHub instance does not return run details: enable sending the id input (sendId)."
)
