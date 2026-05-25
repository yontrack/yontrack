package net.nemerosa.ontrack.extension.av.audit

import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingInfo

/**
 * This service is responsible to log events throughout the whole life-cycle
 * of an auto versioning process.
 *
 * Those events include (non exhaustively):
 *
 * * the creation of an auto versioning process and its queing
 * * the start of its processing
 * * the post processing if any
 * * the PR creation and processing if any
 * * any error attached to this PR
 *
 * Throughout its processing, an auto versioning process is uniquely
 * identified by its [UUID][AutoVersioningOrder.uuid] and is linked to
 * the _target project_.
 */
interface AutoVersioningAuditService {

    /**
     * Cancels all orders whose processing has not started yet.
     *
     * Only the requests having the same:
     *
     * * source project
     * * qualifier
     * * target branch
     * * list of paths
     *
     * as the [order] are considered.
     *
     * @param order Order used to identify the requests to cancel
     * @return Number of requests canceled
     */
    fun throttling(order: AutoVersioningOrder): Int

    /**
     * The [order] was just created. It's the first event in the story of this auto versioning order.
     *
     * @param order Auto versioning order
     */
    fun onCreated(order: AutoVersioningOrder): AutoVersioningAuditEntry

    /**
     * The [order] was created with a future schedule and is now waiting for its time.
     *
     * @param order Auto versioning order
     */
    fun onPendingSchedule(order: AutoVersioningOrder)

    /**
     * The [order] was just scheduled.
     *
     * @param order Auto versioning order
     * @param routing Queue routing key being used
     */
    fun onScheduled(order: AutoVersioningOrder, routing: String)

    /**
     * The [order] was received on the queue and is ready for processing
     *
     * @param order Auto versioning order
     * @param queue Actual queue where the message was sent
     */
    fun onReceived(order: AutoVersioningOrder, queue: String)

    /**
     * Marks the [order] has been in error.
     *
     * @param order Auto versioning order being processed
     * @param error The error on processing
     */
    fun onError(order: AutoVersioningOrder, error: Throwable)

    /**
     * The processing of this [order] has started.
     *
     * @param order Auto versioning order being processed
     */
    fun onProcessingStart(order: AutoVersioningOrder)

    /**
     * The processing of this [order] has been aborted.
     *
     * @param order Auto versioning order being processed
     * @param message Explanatory message
     */
    fun onProcessingAborted(order: AutoVersioningOrder, message: String)

    /**
     * Creating the upgrade branch for this [order].
     *
     * @param order Auto versioning order being processed
     * @param upgradeBranch Actual name of the upgrade branch
     */
    fun onProcessingCreatingBranch(order: AutoVersioningOrder, upgradeBranch: String)

    /**
     * File at path [targetPath] on branch [upgradeBranch] is being updated with
     * the new version for the [order] being processed.
     *
     * @param order Auto versioning order being processed
     * @param upgradeBranch Actual name of the upgrade branch
     * @param targetPath Path to the file being updated
     */
    fun onProcessingUpdatingFile(order: AutoVersioningOrder, upgradeBranch: String, targetPath: String)

    /**
     * Post-processing has started for the [order] for the [upgradeBranch] branch.
     *
     * @param order Auto versioning order being processed
     * @param upgradeBranch Actual name of the upgrade branch
     */
    fun onPostProcessingStart(order: AutoVersioningOrder, upgradeBranch: String)

    /**
     * Post-processing has been launched and we have more information
     *
     * @param order Auto versioning order being processed
     * @param postProcessingInfo Information linked to the post-processing
     */
    fun onPostProcessingLaunched(order: AutoVersioningOrder, postProcessingInfo: PostProcessingInfo)

    /**
     * Post-processing has finished for the [order] for the [upgradeBranch] branch.
     *
     * @param order Auto versioning order being processed
     * @param upgradeBranch Actual name of the upgrade branch
     */
    fun onPostProcessingEnd(order: AutoVersioningOrder, upgradeBranch: String)

    /**
     * Preparing to push to the target branch directly.
     *
     * @param order Auto-versioning order being processed
     * @param upgradeBranch Actual name of the upgrade branch
     */
    fun onPushing(order: AutoVersioningOrder, upgradeBranch: String)

    /**
     * Auto-versioning has been pushed to the target branch.
     *
     * @param order Auto-versioning order being processed
     * @param upgradeBranch Actual name of the upgrade branch
     * @param commit Commit SHA of the pushed changes
     * @param commitLink Link to the pushed commit
     */
    fun onPushed(
        order: AutoVersioningOrder,
        upgradeBranch: String,
        commit: String,
        commitLink: String,
    )

    /**
     * Creating the SCM PR for the [order] from the [upgradeBranch] branch.
     *
     * @param order Auto versioning order being processed
     * @param upgradeBranch Actual name of the upgrade branch
     */
    fun onPRCreating(order: AutoVersioningOrder, upgradeBranch: String)

    /**
     * The SCM PR for the [order] from the [upgradeBranch] branch was created, but it could not
     * be merged in a timely fashion.
     *
     * @param order Auto versioning order being processed
     * @param upgradeBranch Actual name of the upgrade branch
     * @param prName Name of the pull request having been created
     * @param prLink Link to the pull request having been created
     */
    fun onPRTimeout(order: AutoVersioningOrder, upgradeBranch: String, prName: String, prLink: String)

    /**
     * The SCM PR for the [order] from the [upgradeBranch] branch has been created. This is the end state
     * when auto-approval is disabled.
     *
     * @param order Auto versioning order being processed
     * @param upgradeBranch Actual name of the upgrade branch
     * @param prName Name of the pull request having been created
     * @param prLink Link to the pull request having been created
     */
    fun onPRCreated(order: AutoVersioningOrder, upgradeBranch: String, prName: String, prLink: String)

    /**
     * The SCM PR for the [order] from the [upgradeBranch] branch has been created and approved. This is the end state
     * when auto-approval is enabled but merge is delegated to the SCM.
     *
     * @param order Auto versioning order being processed
     * @param upgradeBranch Actual name of the upgrade branch
     * @param prName Name of the pull request having been created
     * @param prLink Link to the pull request having been created
     */
    fun onPRApproved(order: AutoVersioningOrder, upgradeBranch: String, prName: String, prLink: String)

    /**
     * The SCM PR for the [order] from the [upgradeBranch] branch has been merged. This is the end state
     * when auto-approval is enabled and merge process is managed by Ontrack.
     *
     * @param order Auto versioning order being processed
     * @param upgradeBranch Actual name of the upgrade branch
     * @param prName Name of the pull request having been created
     * @param prLink Link to the pull request having been created
     */
    fun onPRMerged(order: AutoVersioningOrder, upgradeBranch: String, prName: String, prLink: String)
}
