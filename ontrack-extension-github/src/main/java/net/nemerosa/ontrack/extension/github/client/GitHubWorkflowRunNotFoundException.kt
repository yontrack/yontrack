package net.nemerosa.ontrack.extension.github.client

import net.nemerosa.ontrack.common.BaseException
import java.util.concurrent.TimeoutException

/**
 * A workflow has been dispatched, but its run could not be found in the list of runs in time.
 *
 * The run may still exist and run to completion: GitHub was only too slow to list it.
 *
 * @param repository Repository containing the workflow
 * @param workflow Workflow file name
 * @param branch Branch the workflow was dispatched on
 * @param cause Timeout of the lookup
 */
class GitHubWorkflowRunNotFoundException(
    val repository: String,
    val workflow: String,
    val branch: String,
    cause: TimeoutException,
) : BaseException(
    cause,
    "Could not find the run of workflow $repository/$workflow on branch $branch in time"
)
