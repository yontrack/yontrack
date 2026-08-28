package net.nemerosa.ontrack.extension.av.event

import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequest

/**
 * Sending events about auto versioning.
 */
interface AutoVersioningEventService {

    /**
     * Broadcasts an error event.
     */
    fun sendError(
        order: AutoVersioningOrder,
        message: String,
        error: Exception,
    )

    /**
     * Broadcasts an event about a version change having been rejected by the version rule.
     *
     * @param reason Reason for the rejection, as returned by the rule
     */
    fun sendRejected(
        order: AutoVersioningOrder,
        reason: String,
    )

    /**
     * Broadcasts an error event about a PR not being able to be merged in time.
     */
    fun sendPRMergeTimeoutError(
        order: AutoVersioningOrder,
        pr: SCMPullRequest,
    )

    /**
     * Broadcasts the success of an auto-versioning request based on a PR
     */
    fun sendSuccess(
        order: AutoVersioningOrder,
        message: String,
        pr: SCMPullRequest,
    )

    /**
     * Broadcasts the success of an auto-versioning request based on a PR
     */
    fun sendSuccessPush(
        order: AutoVersioningOrder,
        message: String,
        commit: String,
        commitLink: String,
    )

}