package net.nemerosa.ontrack.extension.github.client

import net.nemerosa.ontrack.common.BaseException

/**
 * The dispatch of a workflow kept failing on transient errors (502, 503, 504 or I/O errors),
 * once all its attempts have been made.
 *
 * @param repository Repository containing the workflow
 * @param workflow Workflow file name
 * @param attempts Number of dispatch attempts which have been made
 * @param cause Last failure
 */
class GitHubWorkflowDispatchException(
    val repository: String,
    val workflow: String,
    val attempts: Int,
    cause: Exception,
) : BaseException(
    cause,
    "Could not dispatch workflow $repository/$workflow after $attempts attempts: ${cause.message ?: cause::class.java.name}"
)
